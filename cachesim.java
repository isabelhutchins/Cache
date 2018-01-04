import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.lang.Object;


public class cachesim{
	
	//block class
	public static class Block{ 
		int tag;
		int set;
		int memaddress;
		boolean validbit = false;
		boolean dirtybit = false;
		String blockdata;
		public Block(int blocksize, int tagbits, int setbits, int startbyte){
			tag = tagbits;
			set=setbits;
			for(int i=0; i<blocksize; i++){
				blockdata=blockdata+"00"; //this is a hex address, so its length will be blocksize x 2
			}
			memaddress=startbyte;
		}
		public void setValid(boolean set){
			validbit=set;
		}
		public void setDirty(boolean set){
			dirtybit = set;
		}
		public int getTag(){
			return tag;
		}
		public int getIndex(){
			return set;
		}
		public boolean getValid(){
			return validbit;
		}
		public boolean getDirty(){
			return dirtybit;
		}
		public String getData(){
			return blockdata;
		}
		public int getMemAddress(){
			return memaddress;
		}
		
		public void setData(int offset, String data){
			String str1 = blockdata.substring(0, offset*2);
			String strchange=data;
			String str2 = blockdata.substring((offset*2)+data.length());
			
			blockdata= str1+ strchange+str2;
		}
	}
	
	
	
	//initializing instance variables
	public static HashMap<Integer, ArrayList<Block>> L1;
	public static String MainMemory [];
	
	public static void main(String args[]){
		
		//collect data from command line 
		
		String fname = args[0];
		int cachesize =Integer.parseInt(args[1]);
		int associativity = Integer.parseInt(args[2]);
		String write = args[3]; //wb or wt
		int blocksize = Integer.parseInt(args[4]);
		
		 cachesize=cachesize*1024; //convert KB to B
		 int numblocks = cachesize/blocksize; 
		 int sets=numblocks/associativity;
		 
		 //address bit lengths
		 
		 double offsetlength=Math.log((double)blocksize) / Math.log(2);
		 double setlength = Math.log((double)sets) / Math.log(2);
		 int taglength= 24-(int)setlength-(int)offsetlength;
		  
		
		 //create and initialize memories
		L1 = new HashMap<Integer, ArrayList<Block>>();
		
		for (int i=0; i<sets; i++){
			L1.put(i, new ArrayList<Block>());
		}
		
		MainMemory = new String[((int)Math.pow(2, 24))-1];
		
		for (int i=0; i<MainMemory.length; i++)
		{
			MainMemory[i]="00"; //each index in main memory = "00"
		}
		
		 
		//scan file
		 try {
			 String valueloaded;
			 String toWrite;//value that you need to write on a store command
			 String hitOrMiss;
			Scanner s = new Scanner (new File(fname));
			
				while (true){
				if (s.hasNext()==false){
					break;
				}
				String command = s.next();
				String hexaddress=s.next();
				
				//convert hex address to binary address
				String hexaddressParse=hexaddress.substring(2);
				
				
				
				 int intaddress=Integer.parseInt(hexaddressParse, 16); //convert hex to decimal
				 String address=Integer.toBinaryString(intaddress); //convert decimal to binary
				 int dif = 24-address.length();
				 for (int i=0; i<dif; i++){ //concatenates zeroes to the beginning to make it 24 bits
					 address = "0"+address;
				 }
				 
				 //split up address into key components: offset, set index, tag
				String offsetbits=address.substring(address.length()-(int)offsetlength, address.length());
			
				int offset;
				
					if (offsetbits.length()==0){
					offset=0;
						}else{
					offset = Integer.parseInt(offsetbits, 2);
					}

			        
				
				String setbits=address.substring(address.length()-(int)offsetlength-(int)setlength, address.length()-(int)offsetlength);
				int set;

				
				if (setbits.length()==0){
					set=0;
				}else{
				 set = Integer.parseInt(setbits, 2);
				 }

		        
				
				String tagbits=address.substring(0, taglength);
				
				int tag;
				if (tagbits.length()==0){
					tag=0;
				}else{
				 tag = Integer.parseInt(tagbits, 2);
				}
				
				
				int startbyte;
				if (hexaddressParse.length()==0){
					startbyte=0;
				}
				 startbyte = Integer.parseInt(hexaddressParse, 16) - offset; //beginning of block being accessed 
				
				
				//get byte size of the access
				int sizeOfAccess=s.nextInt();
				
				
				if (command.equals("store")){
					toWrite=s.next();
					
					int indexOfAccess = checkL1(set, tag);//returns index of access in array list if its there. -1 if miss.
					
					if (write.equals("wt")){ 
						if (indexOfAccess==-1){//NOT IN L1 CACHE
							hitOrMiss="miss";
							writeToMem(intaddress, sizeOfAccess, toWrite); //on miss, write to main, don't bring to cache
						}else{ //write thru = write to cache and main memory on hit
							hitOrMiss="hit";
							writeToMem(intaddress, sizeOfAccess, toWrite);
							writeToCache(set, indexOfAccess, offset, toWrite, false);
						}
					}else{ //AKA, if write.equals "wb"
						if (indexOfAccess==-1){//NOT IN L1 CACHE
							
							hitOrMiss="miss";
					        
						       	moveToCache(set, tag, blocksize, associativity, offset, toWrite, startbyte, intaddress);
							
						      	writeToCache(set, 0, offset, toWrite, true);
						}else{
							hitOrMiss="hit";
							writeToCache(set, indexOfAccess, offset, toWrite, true); //write to cache and not main memory on hit
							
						}
					}
					System.out.println("store " + hexaddress + " " + hitOrMiss);
				}
				
				
				
				//HANDLING ALL LOAD COMMANDS!
				if (command.equals("load")){
				
				//CHECK L1
				int inL1=checkL1(set, tag); //returns index in arrayList if present
				if (inL1==-1){ //if not found in the L1
					hitOrMiss="miss";
					valueloaded=loadFromMem(intaddress, sizeOfAccess);
					moveToCache(set, tag, blocksize, associativity, offset, valueloaded, startbyte, intaddress);
				}else{ //if the block was found in the L1 Cache
					hitOrMiss="hit"; 
					valueloaded=loadFromCache(sizeOfAccess, set, inL1, offset); //load hex value from cache
					
				}
				System.out.println("load " + hexaddress + " " + hitOrMiss + " " + valueloaded);
				}
				
			 	if (s.hasNextLine()==true){
					s.nextLine();
					continue;
				}
			 	break;
			 	}
				
					s.close();
				
			
				}catch (FileNotFoundException e) {
				System.out.println("File not found!");
				e.printStackTrace();
			}
    
	}
	
	/*gets all of the blocks in the specified set.
	 *loops through the set and checks if the valid bit is true (AKA, 1)
	 *if the valid bit is true, it checks to see if the tagbits match those of the address.
	 *if the tag bits match, the method returns the index of the block in the blocklist
	 *if not, the method returns -1 to indicate that the block is not in the set
	 */
	public static int checkL1(int set, int tag){
		ArrayList<Block> blockList = L1.get(set);
		
		if (blockList.size()==0)
			return -1;
		
		for (int i=0; i<blockList.size(); i++){
			if (blockList.get(i).getTag()==tag){
				if (blockList.get(i).getValid()==true){
					return i;
				}
			}
		}
		return -1;
				
	}
	
	public static void writeToMem(int address, int sizeOfAccess, String toWrite){
		int j=0;
			for (int i=address; i<address+sizeOfAccess; i++){
				String replace = toWrite.substring(j, j+2);
				MainMemory[i]=replace;
				j+=2;
			}
			
		}
	
	public static void writeToCache(int set, int indexOfAccess, int offset, String toWrite, boolean wb){
		L1.get(set).get(indexOfAccess).setData(offset, toWrite);
		
		if (wb==true){
			L1.get(set).get(indexOfAccess).setDirty(true);
		}
		
		//sets as MRU
		Block hold = L1.get(set).get(indexOfAccess);
		L1.get(set).remove(indexOfAccess);
		L1.get(set).add(0, hold);
	}
	
	public static void moveToCache(int set, int tag, int blocksize, int associativity, int offset, String loadedData, int startbyte, int hexaddress ){
		Block newblock = new Block(blocksize, tag, set, startbyte); 
		
		if (isSetFull(set, associativity)==true){
			if (L1.get(set).get(associativity-1).dirtybit==true){ //checks if LRU dirty bit is dirty 
				writeToMem(L1.get(set).get(associativity-1).memaddress, blocksize, L1.get(set).get(associativity-1).blockdata); //if dirty, writes to memory
			}
			L1.get(set).remove(associativity-1); //removes LRU
		}
		newblock.setValid(true);
		
		newblock.setData(0, loadFromMem(startbyte, blocksize)); //sets the data in the new block to ALL of the bytes in the block in memory
		L1.get(set).add(0, newblock); //adds block to the beginning of the arraylist
	
	}
	
	public static boolean isSetFull(int set, int associativity){
		ArrayList<Block>setToCheck=L1.get(set);
		if (setToCheck.size()==associativity){
			return true;
		}
		
		return false;
	}
	
	public static String loadFromMem(int address, int storesize){
		String loadedData="";
		for (int i=address; i<address+storesize; i++){
			loadedData=loadedData+MainMemory[i];
	}
		return loadedData;
	}
	
	
	public static String loadFromCache(int sizeOfAccess, int set, int indexInSet, int offset){
		Block hold = L1.get(set).get(indexInSet);
		String blockdata = hold.getData(); //gets data
		String valueLoaded=blockdata.substring((offset*2), (offset*2)+(sizeOfAccess*2)); //returns substring of data requested
		L1.get(set).remove(indexInSet);
		L1.get(set).add(0, hold); //move block to the top for LRU
		
		return valueLoaded;
	}
	 
	
	}
	

