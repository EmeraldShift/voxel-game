package com.lithia.cs.core.world;

import java.util.*;

import org.lwjgl.util.vector.*;

import com.lithia.cs.core.*;
import com.lithia.cs.core.gen.*;

public class World extends Renderable
{
	
	/**
	 * The (single) player to occupy the world. This hook may be used to access
	 * the player's state, such as position, rotation, etc.
	 */
	private Player player;
	
	/**
	 * The active collection of chunks being used by the World. Each chunk is a
	 * large collection of blocks, divided up for runtime efficiency.
	 */
	private Chunk[][][] chunks;
	
	/**
	 * A separately-running thread devoted to continuously updating chunks
	 * within the {@code Update} queue, instantiated directly below this object.
	 */
	private Thread updateThread;
	
	/**
	 * The {@code Update} queue holds chunks who have been flagged for an update
	 * by an outside class or method. Chunks in this queue are processed by the
	 * {@code updateThread} thread before being switched to the {@code DLUpdate}
	 * queue for display list handling.
	 */
	private List<Chunk> chunkUpdateQueue = new LinkedList<Chunk>();
	
	/**
	 * The {@code DLUpdate} queue holds chunks whose vertex arrays have been
	 * rebuild and that need their display list to be up-to-date with the new
	 * vertex data. Chunks in this queue are processed by the main thread to
	 * maintain thread safety between the updating and the rendering of chunks.
	 */
	private List<Chunk> chunkDLUpdateQueue = new LinkedList<Chunk>();
	
	public World(String name, String seed, Player player)
	{
		this.player = player;
		chunks = new Chunk[(int) Config.WORLD_SIZE.x][(int) Config.WORLD_SIZE.y][(int) Config.WORLD_SIZE.z];
		
		Generator terrainGen = new GeneratorTerrain(seed);
		
		for (int x = 0; x < Config.WORLD_SIZE.x; x++)
		{
			for (int z = 0; z < Config.WORLD_SIZE.z; z++)
			{
				ArrayList<Generator> gs = new ArrayList<Generator>();
				gs.add(terrainGen);
				
				Chunk c = new Chunk(this, new Vector3f(x, 0, z), gs);
				chunks[x][0][z] = c;
				queueChunkForUpdate(c);
			}
		}
		
		player.resetPosition();
		updateThread = new Thread(new Runnable()
		{
			
			public void run()
			{
				while (true)
				{
					if (!chunkUpdateQueue.isEmpty() && chunkDLUpdateQueue.isEmpty())
					{
						Chunk[] chunks = chunkUpdateQueue.toArray(new Chunk[0]);
						
						double distance = Float.MAX_VALUE;
						int index = -1;
						
						for(int i = 0; i < chunks.length; i++)
						{
							Chunk c = chunks[i];
							double dist = c.calcDistanceToPlayer();
							
							if(dist < distance)
							{
								distance = dist;
								index = i;
							}
						}
						
						if(index != -1)
						{
							Chunk c = chunks[index];
							processChunk(c);
							chunkUpdateQueue.remove(c);
						}
					}
					
					try
					{
						Thread.sleep(0);
					}
					catch (Exception e)
					{
					}
				}
			}
			
		});
		
		updateThread.start();
	}
	
	/**
	 * Processes a chunk, generating and initializing its initial state if it
	 * has not already been generated, and rebuilding its vertex arrays if it is
	 * flagged for an update.
	 * 
	 * @param c
	 */
	private void processChunk(Chunk c)
	{
		if (c == null) return;
		
		Chunk[] neighbors = c.getNeighbors();
		for (Chunk n : neighbors)
		{
			if (n == null) continue;
			
			n.generate();
			
			if (n.update)
			{
				n.generateVertexArrays();
				chunkDLUpdateQueue.add(n);
			}
		}
		
		c.generate();
		
		if (c.update)
		{
			c.generateVertexArrays();
			chunkDLUpdateQueue.add(c);
		}
	}
	
	/**
	 * Simple test of rendering capabilities featuring: The infinitely receding
	 * square! TODO: Implement an efficient method of rendering millions of
	 * voxels here...
	 */
	public void render()
	{
		for (int x = 0; x < Config.WORLD_SIZE.x; x++)
		{
			for (int z = 0; z < Config.WORLD_SIZE.x; z++)
			{
				Chunk c = chunks[x][0][z];
				if (c != null) c.render();
			}
		}
	}
	
	/**
	 * Iterate through chunks in update queue, and rebuild their display lists.
	 */
	public void update()
	{
		try
		{
			Chunk c = chunkDLUpdateQueue.remove(0);
			c.generateDisplayList();
		}
		catch (Exception e)
		{
		}
	}
	
	public Chunk getChunk(int x, int y, int z)
	{
		Chunk c = null;
		
		try
		{
			c = chunks[x][y][z];
		}
		catch (Exception e)
		{
		}
		
		return c;
	}
	
	public int getBlock(int x, int y, int z)
	{
		int chunkPosX = calcChunkPosX(x);
		int chunkPosY = calcChunkPosY(y);
		int chunkPosZ = calcChunkPosZ(z);
		
		int blockPosX = calcBlockPosX(x, chunkPosX);
		int blockPosY = calcBlockPosY(y, chunkPosY);
		int blockPosZ = calcBlockPosZ(z, chunkPosZ);
		
		Chunk c = null;
		
		try
		{
			c = chunks[chunkPosX][chunkPosY][chunkPosZ];
		}
		catch (Exception e)
		{
		}
		
		if (c != null)
			return c.getBlock(blockPosX, blockPosY, blockPosZ);
		
		return 1;
	}
	
	private int calcBlockPosX(int x1, int x2)
	{
		return (x1 - x2 * (int) Chunk.CHUNK_SIZE.x);
	}
	
	private int calcBlockPosY(int y1, int y2)
	{
		return (y1 - y2 * (int) Chunk.CHUNK_SIZE.y);
	}
	
	private int calcBlockPosZ(int z1, int z2)
	{
		return (z1 - z2 * (int) Chunk.CHUNK_SIZE.z);
	}
	
	private int calcChunkPosX(int x)
	{
		return x / (int) Chunk.CHUNK_SIZE.x;
	}
	
	private int calcChunkPosY(int y)
	{
		return y / (int) Chunk.CHUNK_SIZE.y;
	}
	
	private int calcChunkPosZ(int z)
	{
		return z / (int) Chunk.CHUNK_SIZE.z;
	}
	
	/**
	 * Send a chunk into the update queue, preparing it for an update.
	 * 
	 * @param c
	 *            The chunk to be updated
	 */
	private void queueChunkForUpdate(Chunk c)
	{
		if (c != null) chunkUpdateQueue.add(c);
	}

	public Player getPlayer()
	{
		return player;
	}
	
}
