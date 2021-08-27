package cruzapi;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import cruzapi.Disk.BitmapType;

public class Inode
{
	public enum Type
	{
		FILE, DIR;
	}
	
	public static final int INODE_SIZE = 49;
	
	private final int index;
	private byte type;
	private final int[] pointer = new int[12];
	
	public Inode(int index)
	{
		this.index = index;
	}
	
	public int index()
	{
		return index;
	}
	
	public int[] pointer()
	{
		return pointer;
	}
	
	public boolean addPointer(int index)
	{
		for(int i = 0; i < pointer.length; i++)
		{
			if(pointer[i] == 0)
			{
				pointer[i] = index;
				return true;
			}
		}
		
		return false;
	}
	
	public void readFully() throws IOException
	{
		Disk disk = Main.getDisk();
		SuperBlock sb = disk.getSuperBlock();
		
		try(RandomAccessFile access = new RandomAccessFile(disk.getFile(), "rw");)
		{
			access.skipBytes(sb.getSize() + sb.getInodeBitmapSize() + sb.getBlockBitmapSize() + (index - 1) * INODE_SIZE);
			type = access.readByte();
			
			for(int i = 0; i < pointer.length; i++)
			{
				pointer[i] = access.readInt();
			}
		}
		catch(IOException e)
		{
			throw e;
		}
	}
	
	
	public void setInUse(boolean value) throws IOException
	{
		Main.getDisk().rwBitmap(BitmapType.INODE, index - 1, value);
	}
	
	public boolean isInUse() throws IOException
	{
		return Main.getDisk().getBitmap(BitmapType.INODE)[index - 1];
	}
	
	public void rw() throws IOException
	{
		Disk disk = Main.getDisk();
		SuperBlock sb = disk.getSuperBlock();
		
		try(RandomAccessFile access = new RandomAccessFile(disk.getFile(), "rw");)
		{
			access.seek(sb.getSize() + sb.getInodeBitmapSize() + sb.getBlockBitmapSize() + (index - 1) * INODE_SIZE);
			access.writeByte(type);
			
			for(int j : pointer)
			{
				access.writeInt(j);
			}
		}
		catch(IOException e)
		{
			throw e;
		}
	}
	
	public void clear()
	{
		type = 0;
		
		for(int i = 0; i < pointer.length; i++)
		{
			pointer[i] = 0;
		}
	}
	
	@Override
	public String toString()
	{
		return "Inode [index=" + index + ", pointer=" + Arrays.toString(pointer) + "]";
	}
}