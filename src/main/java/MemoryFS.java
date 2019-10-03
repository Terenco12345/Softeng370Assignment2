import com.github.lukethompsxn.edufuse.filesystem.FileSystemStub;
import com.github.lukethompsxn.edufuse.struct.*;
import com.github.lukethompsxn.edufuse.util.ErrorCodes;
import com.github.lukethompsxn.edufuse.util.FuseFillDir;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.types.dev_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import util.MemoryINode;
import util.MemoryINodeDirectory;
import util.MemoryINodeTable;
import util.MemoryVisualiser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import com.sun.security.auth.module.UnixSystem;

/**
 * @author Luke Thompson and Terence Qu
 * @since 04.09.19
 */
public class MemoryFS extends FileSystemStub {
	private static final int BLOCK_SIZE = 4096;
	
    private static final String HELLO_PATH = "/hello";
    private static final String HELLO_STR = "Hello World!\n";

    private MemoryINodeTable iNodeTable = new MemoryINodeTable();
    private MemoryVisualiser visualiser;
    private UnixSystem unix = new UnixSystem();

    @Override
    public Pointer init(Pointer conn) {

        // setup an example file for testing purposes
        MemoryINode iNode = new MemoryINode();
        FileStat stat = new FileStat(Runtime.getSystemRuntime());
        
        // you will have to add more stat information here eventually
        stat.st_mode.set(FileStat.S_IFREG | 0444 | 0200);
        stat.st_size.set(HELLO_STR.getBytes().length);
        stat.st_nlink.set(1);
        stat.st_uid.set(unix.getUid());
        stat.st_gid.set(unix.getGid());
        stat.st_blocks.set((HELLO_STR.getBytes().length/BLOCK_SIZE));
        stat.st_blksize.set(BLOCK_SIZE);
        
        long secondTime = System.currentTimeMillis()/1000;
        long nanoSecondTime = System.nanoTime();
        
        // Access time
        stat.st_atim.tv_sec.set(secondTime);
        stat.st_atim.tv_nsec.set(nanoSecondTime);
        
        // Status change time
        stat.st_ctim.tv_sec.set(secondTime);
        stat.st_ctim.tv_nsec.set(nanoSecondTime);
        
        // Modification time
        stat.st_mtim.tv_sec.set(secondTime);
        stat.st_mtim.tv_nsec.set(nanoSecondTime);
        
        iNode.setStat(stat);
        iNode.setContent(HELLO_STR.getBytes());
        iNodeTable.updateINode(HELLO_PATH, iNode);

        if (isVisualised()) {
            visualiser = new MemoryVisualiser();
            visualiser.sendINodeTable(iNodeTable);
        }

        return conn;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        int res = 0;
        if (Objects.equals(path, "/")) { // minimal set up for the mount point root
            stat.st_mode.set(FileStat.S_IFDIR | 0755);
            stat.st_nlink.set(2);
        } else if (iNodeTable.containsINode(path)) {
            FileStat savedStat = iNodeTable.getINode(path).getStat();
            // fill in the stat object with values from the savedStat object of your inode
            stat.st_mode.set(savedStat.st_mode.intValue());
            stat.st_size.set(savedStat.st_size.intValue());
            stat.st_nlink.set(savedStat.st_nlink.intValue());
            stat.st_uid.set(savedStat.st_uid.intValue());
            stat.st_gid.set(savedStat.st_gid.intValue());
            stat.st_blocks.set(savedStat.st_blocks.intValue());
            stat.st_blksize.set(savedStat.st_blksize.intValue());
            stat.st_atim.tv_sec.set(savedStat.st_atim.tv_sec.intValue());
            stat.st_atim.tv_nsec.set(savedStat.st_atim.tv_nsec.intValue());
            stat.st_ctim.tv_sec.set(savedStat.st_ctim.tv_sec.intValue());
            stat.st_ctim.tv_nsec.set(savedStat.st_ctim.tv_nsec.intValue());
            stat.st_mtim.tv_sec.set(savedStat.st_mtim.tv_sec.intValue());
            stat.st_mtim.tv_nsec.set(savedStat.st_mtim.tv_nsec.intValue());
        } else {
            res = -ErrorCodes.ENOENT();
        }
        return res;
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo fi) {
    	System.out.println("Calling readdir at "+path);
    	
    	// For each file in the directory call filler.apply.
        // The filler.apply method adds information on the files
        // in the directory, it has the following parameters:
        //      buf - a pointer to a buffer for the directory entries
        //      name - the file name (with no "/" at the beginning)
        //      stbuf - the FileStat information for the file
        //      off - just use 0
        filler.apply(buf, ".", null, 0);
        filler.apply(buf, "..", null, 0);
        
        
        
        // Obtain all of the directory's children.
        HashMap<String, MemoryINode> dirChildren = new HashMap<>();
        if(path.equals("/")) {
        	for(String entry: iNodeTable.entries()) {
            	String fileName = entry.substring(path.length());
            	
            	// Test files in the iNodeTable against the criteria of not being a child of any child of root.
            	if(!fileName.equals("") && !fileName.contains("/")) {
            		dirChildren.put(fileName, iNodeTable.getINode(entry));
            	}
            }
        } else {
        	for(String entry: iNodeTable.entries()) {
            	if(entry.startsWith(path)) {
            		String fileName = entry.substring(path.length());
            		
            		// Test files in the iNodeTable against the criteria of not being a child of any child of root.
            		// And not being itself.
            		if(!fileName.equals("") && fileName.startsWith("/")) {
            			System.out.println("entry: " +fileName);
            			fileName = fileName.substring(1);
            			if(!fileName.contains("/")) {
            				dirChildren.put(fileName, iNodeTable.getINode(entry));
            			}
            		}
            	}
            }
        }
        
        
        // Apply filler to all children
        for(Entry<String, MemoryINode> entry: dirChildren.entrySet()) {
        	System.out.println("Filling with "+entry.getKey());
            filler.apply(buf, entry.getKey(), entry.getValue().getStat(), 0);
        }
        
        return 0;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        System.out.println("Attempting to read at "+path);
    	
    	if (!iNodeTable.containsINode(path)) {
            return -ErrorCodes.ENOENT();
        }
        // you need to extract data from the content field of the inode and place it in the buffer
        // something like:
        // buf.put(0, content, offset, amount);
        
        int amount = 0;

        MemoryINode node = iNodeTable.getINode(path);
        amount = node.getContent().length;
        
        buf.put(0, node.getContent(), (int)offset, amount);
        
        // Change access time
        long secondTime = System.currentTimeMillis()/1000;
        long nanoSecondTime = System.nanoTime();
        node.getStat().st_atim.tv_sec.set(secondTime);
        node.getStat().st_atim.tv_nsec.set(nanoSecondTime);
        
        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }

        return amount;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
    	System.out.println("Attempting to write at "+path);
    	
        if (!iNodeTable.containsINode(path)) {
            return -ErrorCodes.ENOENT(); // ENONET();
        }
        
        MemoryINode node = iNodeTable.getINode(path);
        
        // Insert data into bufferContent array
        byte[] bufferContent = new byte[(int)size];
        buf.get(0, bufferContent, 0, (int)size);
        
        // Initialize new arrays
        byte[] oldContent = node.getContent();
        byte[] newContent = new byte[(int)size+node.getContent().length];
        
        // Concatenate oldContent and bufferContent
        for(int i = 0; i < oldContent.length; i++) {
        	newContent[i] = oldContent[i];
        }
        for(int i = 0; i < bufferContent.length; i++) {
        	newContent[i+oldContent.length] = bufferContent[i];
        }
        
        node.setContent(newContent);
        
        // Change time and file size
        long secondTime = System.currentTimeMillis()/1000;
        long nanoSecondTime = System.nanoTime();
        node.getStat().st_size.set(node.getContent().length);
        node.getStat().st_mtim.tv_sec.set(secondTime);
        node.getStat().st_mtim.tv_nsec.set(nanoSecondTime);
        
        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }

        return (int) size;
    }

    @Override
    public int mknod(String path, @mode_t long mode, @dev_t long rdev) {
    	System.out.println("Attempting to make node at "+path);
    	
        if (iNodeTable.containsINode(path)) {
            return -ErrorCodes.EEXIST();
        }

        MemoryINode mockINode = new MemoryINode();
        
        // Set up the stat information for this inode
        FileStat stat = new FileStat(Runtime.getSystemRuntime());
        
        stat.st_mode.set(mode);
        stat.st_rdev.set(rdev);
        stat.st_size.set(0);
        stat.st_nlink.set(1);
        stat.st_uid.set(unix.getUid());
        stat.st_gid.set(unix.getGid());
        stat.st_blocks.set(0);
        stat.st_blksize.set(BLOCK_SIZE);
        
        long secondTime = System.currentTimeMillis()/1000;
        long nanoSecondTime = System.nanoTime();
        
        // Access time
        stat.st_atim.tv_sec.set(secondTime);
        stat.st_atim.tv_nsec.set(nanoSecondTime);
        
        // Status change time
        stat.st_ctim.tv_sec.set(secondTime);
        stat.st_ctim.tv_nsec.set(nanoSecondTime);
        
        // Modification time
        stat.st_mtim.tv_sec.set(secondTime);
        stat.st_mtim.tv_nsec.set(nanoSecondTime);
        
        mockINode.setStat(stat);
        
        iNodeTable.updateINode(path, mockINode);

        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }

        return 0;
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        return super.statfs(path, stbuf);
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        // The Timespec array has the following information.
        // You need to set the corresponding fields of the inode's stat object.
        // You can access the data in the Timespec objects with "get()" and "longValue()".
        // You have to find out which time fields these correspond to.
        // timespec[0].tv_nsec
        // timespec[0].tv_sec
        // timespec[1].tv_nsec
        // timespec[1].tv_sec
        return 0;
    }
    
    @Override
    public int link(java.lang.String oldpath, java.lang.String newpath) {
    	System.out.println("Attempting to link node at "+oldpath+" with node at "+newpath);
    	MemoryINode oldNode = iNodeTable.getINode(oldpath);
    	oldNode.getStat().st_nlink.set(oldNode.getStat().st_nlink.intValue()+1);
    	
    	iNodeTable.updateINode(newpath, oldNode);
    	
    	if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }
    	
        return 0;
    }

    @Override
    public int unlink(String path) {
    	System.out.println("Attempting to remove node at "+path);
    	
        if (!iNodeTable.containsINode(path)) {
            return -ErrorCodes.ENONET();
        }
        
        List<String> pathsToRemove = new ArrayList<>();
        MemoryINode node = iNodeTable.getINode(path);
        
        // Find all instances of the node at path in iNodeTable.
        for(String entryPath: iNodeTable.entries()) {
        	if(iNodeTable.getINode(entryPath) == node) {
        		System.out.println("Node at "+entryPath+" should be removed");
        		pathsToRemove.add(entryPath);
        	}
        }
        
        // Remove all instances of the node
        for(String pathToRemove: pathsToRemove) {
        	System.out.println("Node at "+pathToRemove+" removed");
        	iNodeTable.removeINode(pathToRemove);
        }
        
        node.getStat().st_nlink.set(0);
        
        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }
        
        // delete the file if there are no more hard links
        return 0;
    }

    @Override
    public int mkdir(String path, long mode) {
    	System.out.println("Attempting to make directory at "+path);
    	
        if (iNodeTable.containsINode(path)) {
            return -ErrorCodes.EEXIST();
        }

        MemoryINode mockINode = new MemoryINode();
        
        // Set up the stat information for this inode
        FileStat stat = new FileStat(Runtime.getSystemRuntime());
        
        stat.st_mode.set(FileStat.S_IFDIR | mode);
        stat.st_size.set(0);
        stat.st_nlink.set(1);
        stat.st_uid.set(unix.getUid());
        stat.st_gid.set(unix.getGid());
        stat.st_blocks.set(0);
        stat.st_blksize.set(0);
        
        long secondTime = System.currentTimeMillis()/1000;
        long nanoSecondTime = System.nanoTime();
        
        // Access time
        stat.st_atim.tv_sec.set(secondTime);
        stat.st_atim.tv_nsec.set(nanoSecondTime);
        
        // Status change time
        stat.st_ctim.tv_sec.set(secondTime);
        stat.st_ctim.tv_nsec.set(nanoSecondTime);
        
        // Modification time
        stat.st_mtim.tv_sec.set(secondTime);
        stat.st_mtim.tv_nsec.set(nanoSecondTime);
        
        mockINode.setStat(stat);
        
        iNodeTable.updateINode(path, mockINode);

        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }

        return 0;
    }

    @Override
    public int rmdir(String path) {
    	
        return 0;
    }

    @Override
    public int rename(String oldpath, String newpath) {
        return 0;
    }

    @Override
    public int truncate(String path, @size_t long size) {
        return 0;
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int setxattr(String path, String name, Pointer value, @size_t long size, int flags) {
        return 0;
    }

    @Override
    public int getxattr(String path, String name, Pointer value, @size_t long size) {
        return 0;
    }

    @Override
    public int listxattr(String path, Pointer list, @size_t long size) {
        return 0;
    }

    @Override
    public int removexattr(String path, String name) {
        return 0;
    }

    @Override
    public int opendir(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int releasedir(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public void destroy(Pointer initResult) {
        if (isVisualised()) {
            try {
                visualiser.stopConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int access(String path, int mask) {
        return 0;
    }

    @Override
    public int lock(String path, FuseFileInfo fi, int cmd, Flock flock) {
        return 0;
    }

    public static void main(String[] args) {
        MemoryFS fs = new MemoryFS();
        try {
            fs.mount(args, true);
        } finally {
            fs.unmount();
        }
    }
}
