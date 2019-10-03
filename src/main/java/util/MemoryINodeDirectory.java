package util;

import java.util.ArrayList;
import java.util.List;

public class MemoryINodeDirectory extends MemoryINode{
	
	private List<MemoryINode> children = new ArrayList<>();
	
	public MemoryINodeDirectory() {
		
	}
}
