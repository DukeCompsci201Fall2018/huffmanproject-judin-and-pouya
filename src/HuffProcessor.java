import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT,HUFF_TREE);
		writeHeader(root,out);

		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
		
		
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int bit = in.readBits(BITS_PER_WORD);
			
			if (bit == -1) {
				break;
			}

			String output = codings[bit];
			out.writeBits(output.length(), Integer.parseInt(output, 2));
		}
		
		String pseudo = codings[PSEUDO_EOF];
		out.writeBits(pseudo.length(), Integer.parseInt(pseudo, 2));		
	}
	
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft == null && root.myRight == null) {
			out.write(1);
			for (int i = 0; i < BITS_PER_WORD; i++) {
				out.write(root.myValue);
			}
		}
		
		else {
			out.write(root.myValue);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] ret = new String[ALPH_SIZE+1];
		makeStringArr(ret, root, "");
		return ret;
	}
	
	private void makeStringArr(String[] stringArr, HuffNode root, String s) {
		if (root.myLeft == null && root.myRight == null) {
			stringArr[root.myValue] = s;
			return;
		}
		
		makeStringArr(stringArr, root.myLeft, s + "0");
		makeStringArr(stringArr, root.myRight, s + "1");
	}
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> queue = new PriorityQueue<>();

		for(int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
				queue.add(new HuffNode(i, counts[i], null, null));
			}
		}

		queue.add(new HuffNode(PSEUDO_EOF, 0));

		while (queue.size() > 1) {
			HuffNode left = queue.remove();
			HuffNode right = queue.remove();
			
			HuffNode t = new HuffNode(0,left.myWeight+right.myWeight, left, right);
			queue.add(t);
		}

		HuffNode ret = queue.remove();
		return ret;
	}
	
	private int[] readForCounts(BitInputStream in) {
		int [] ret = new int[ALPH_SIZE+1];
		
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) break;
			ret[bits]++;
		}
		
		ret[PSEUDO_EOF] = 1;
		return ret;
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
		
	}
	
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("-1 found in header");
		if (bit == 0) {
			return new HuffNode(0, 0, readTreeHeader(in), readTreeHeader(in));
		}
		else {
			return new HuffNode(in.readBits(BITS_PER_WORD + 1), 0, null, null);
		}
	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		
		HuffNode temp = root;
		
		while(true) {
			int bit = in.readBits(1);
			if (bit == -1) throw new HuffException("-1 found in header");
			if (bit == 0) {
				temp = temp.myRight;
				//TODO: Figure out what to put here!
			}
			else {
				temp = temp.myLeft;
			}
			
			if (temp.myLeft == null && temp.myRight == null) {
				if (temp.myValue == PSEUDO_EOF) break;
				else {
					for (int i = 0; i < BITS_PER_WORD; i++) {
						out.write(temp.myValue);
						temp = root;
					}
				}
			}

		}
	}
	
}