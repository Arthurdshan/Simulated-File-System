package cruzapi;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class Disk
{
	public enum BitmapType
	{
		INODE, BLOCK;
		
		public int getPosition() throws IOException
		{
			return switch (this) {
				case BLOCK -> Main.getDisk().getSuperBlock().getBlockBitmapPosition();
				case INODE -> Main.getDisk().getSuperBlock().getInodeBitmapPosition();
			};
		}
		
		public int getSize() throws IOException
		{
			return switch (this) {
				case BLOCK -> Main.getDisk().getSuperBlock().getBlockBitmapSize();
				case INODE -> Main.getDisk().getSuperBlock().getInodeBitmapSize();
			};
		}
	}
	
	private final File file;
	
	private Inode currentInode;
	
	public Disk()
	{
		file = new File(System.getProperty("user.home") + "/Desktop", "disco.dsc");
	}
	
	public void format()
	{
		if(!file.delete())
		{
			throw new RuntimeException("Something went wrong.");
		}
		if(!create())
		{
			throw new RuntimeException("Something went wrong.");
		}
	}
	
	public boolean create()
	{
		try(RandomAccessFile file = new RandomAccessFile(this.file, "rw"))
		{
			SuperBlock sb = new SuperBlock();
			
			file.writeInt(sb.getMagicNumber());
			file.writeInt(sb.getBlocks());
			file.writeInt(sb.getBlockSize());
			
			
			byte[] inodeBitmap = new byte[sb.getInodeBitmapSize()];

			Arrays.fill(inodeBitmap, Byte.MIN_VALUE);
			
			file.write(inodeBitmap);

			byte[] blockBitmap = new byte[sb.getBlockBitmapSize()];

			Arrays.fill(blockBitmap, Byte.MIN_VALUE);
			
			file.write(blockBitmap);

			for(int i = 1; i <= sb.getInodes(); i++)
			{
				file.writeByte(0);
				
				for(int j = 0; j < 13; j++)
				{
					file.writeInt(0);
				}
			}
			
			for(int i = 0; i < sb.getBlocks(); i++)
			{
				file.write(new byte[4096]);
			}
			
			
			currentInode = getEmptyInode();
			Block block = getEmptyBlock();
			
			assert block != null;
			currentInode.addPointer(block.index());
			block.addEntry(new DirEntry(currentInode.index(), "/"));
			block.addEntry(new DirEntry(currentInode.index(), "/"));
			
			currentInode.setInUse(true);
			currentInode.rw();
			
			block.setInUse(true);
			block.rw();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		
		return true;
	}
	
	public Block getEmptyBlock() throws IOException
	{
		boolean[] bitmap = getBitmap(BitmapType.BLOCK);

		for(int i = 0; i < bitmap.length; i++)
		{
			if(!bitmap[i])
			{
				return new Block(i + 1);
			}
		}
		return null;
	}

	public void rwBitmap(final BitmapType type, final int index, final boolean value) throws IOException
	{
		try(RandomAccessFile file = new RandomAccessFile(this.file, "rw"))
		{
			file.seek(type.getPosition() + index / 8);
			
			final int bit = 7 - index % 8;
			
			int b1 = file.readByte() + 128;
			int b2 = Byte.MIN_VALUE;
			
			for(int j = 0; j < 8; j++)
			{
				b2 += bit == j ? value ? 1 << j : 0 : (b1 % 2) == 1 ? 1 << j : 0;
				b1 /= 2;
			}
			
			file.seek(type.getPosition() + index / 8);
			file.write(b2);
		}
	}
	
	public boolean[] getBitmap(BitmapType type) throws IOException
	{
		try(RandomAccessFile file = new RandomAccessFile(this.file, "rw"))
		{
			file.seek(type.getPosition());
			
			final boolean[] bitmap = new boolean[type.getSize() * 8];
			
			int n = 0;
			
			for(int i = 0; i < type.getSize(); i++)
			{
				int b = file.readByte() + 128;
				
				for(int j = 7; j >= 0; j--)
				{
					bitmap[n + j] = (b % 2) == 1;
					b /= 2;
				}
				
				n += 8;
			}
			
			return bitmap;
		}
	}
	
	public Inode getEmptyInode() throws IOException
	{
		boolean[] bitmap = getBitmap(BitmapType.INODE);

		for(int i = 0; i < bitmap.length; i++)
		{
			if(!bitmap[i])
			{
				return new Inode(i + 1);
			}
		}
		return null;
	}
	
	public SuperBlock getSuperBlock() throws IOException
	{
		try(RandomAccessFile file = new RandomAccessFile(this.file, "rw"))
		{
			final int magicNumber = file.readInt();
			final int blocks = file.readInt();
			final int blockSize = file.readInt();
			
			return new SuperBlock(magicNumber, blocks, blockSize);
		}
	}
	
	public File getFile()
	{
		return file;
	}
	
	public Inode getCurrentInode()
	{
		return currentInode;
	}
	
	public void setCurrentInode(Inode inode)
	{
		currentInode = inode;
	}
	
	public boolean rm(Inode inode)
	{
		return true;
	}
	
	public Inode getInodeByPath(Inode current, String[] path)
	{
		return null;
	}
	
	private boolean checkForSameName(String dir) throws IOException
	{
		for(int i = 0; i < currentInode.pointer().length; i++)
		{
			Block block = new Block(currentInode.pointer()[i]);

			block.readFully();

			for(DirEntry entry : block.getEntries(i == 0 ? 2 : 0))
			{
				if(dir.equalsIgnoreCase(entry.getName()))
				{
					return true;
				}
			}
		}
		return false;
	}

	public void mkdir(String dir) throws IOException
	{
		Inode target = getEmptyInode();
		
		if(target == null)
		{
			System.out.println("No empty inode available.");
			return;
		}

		if(checkForSameName(dir))
		{
			System.out.println("Directory already exists.");
			return;
		}

		DirEntry entry = new DirEntry(target.index(), dir);
		
		Block newBlock = getEmptyBlock();
		
		if(newBlock == null)
		{
			System.out.println("No empty block available.");
			return;
		}
		
		target.addPointer(newBlock.index());
		newBlock.setInUse(true);
		newBlock.addEntry(entry);
		
		boolean inodeFull = true;
		
		for(int i = 0; i < currentInode.pointer().length; i++)
		{
			Block block = new Block(currentInode.pointer()[i]);
			
			if(block.index() == 0)
			{
				block = getEmptyBlock();
				
				if(block == null)
				{
					inodeFull = false;
					continue;
				}
			}
			
			block.readFully();
			
			if(i == 0)
			{
				newBlock.addEntry(block.getEntry(0));
			}
			
			if(block.addEntry(entry))
			{
				currentInode.pointer()[i] = block.index();
				currentInode.rw();
				block.setInUse(true);
				target.setInUse(true);
				block.rw();
				target.rw();
				newBlock.rw();
				return;
			}
		}
		
		newBlock.setInUse(false);
		
		if(inodeFull)
		{
			System.out.println("Current inode's pointer is full.");
		}
		else
		{
			System.out.println("No empty block available.");
		}
	}
	
	public DirEntry getEntryByPath(DirEntry entry, final String[] path, int i) throws IOException
	{
		if(i == path.length)
		{
			return entry;
		}
		
		Inode inode = new Inode(entry.getIndex(), true);
		
		for(int j = 0; j < inode.pointer().length; j++)
		{
			Block b = new Block(inode.pointer()[j]);
			
			if(b.index() == 0)
			{
				continue;
			}
			
			b.readFully();
			
			for(DirEntry entries : b.getEntries(j == 0 ? 2 : 0))
			{
				if(entries.getName().equalsIgnoreCase(path[i]))
				{
					return getEntryByPath(entries, path, i + 1);
				}
			}
		}
		
		return null;
	}
}