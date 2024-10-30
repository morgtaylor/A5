import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

/* 
   This class demonstrates using a multithreaded brute force attack on a randomly generated RSA key.
   In practice, you can see how fast the "crack" time increases with increasing bit lengths.
   The initial bit length can be specified on the command line and otherwise defaults to 8.

   Compile using: javac Rsa.java
   Run using: java Rsa [digit-length]
*/
public class Rsa {
    static final BigInteger TWO = new BigInteger("2");
    public static void main (String [] args) {

        int bitLength = 8; // default to starting at 8
	if (args.length == 1)
	    try {
		bitLength = Integer.parseInt(args[0]);
	    }
	    catch (Exception e) {
		System.out.println("Correct usage: java Rsa [bit-length]");
		System.exit(0);
	    }
	
        Random r = new Random(); // should only ever be called once in a Java program!
	
    System.out.println("Resutls: ");
    long t1 = 0;
    long t2 = 0;
    long t3 = 0;
    long avg = 0;
	// LOOP OVER THE BIT LENGTH HERE
    for (int j = 16; j <= 30; j++) {
        bitLength = j;
        for (int k = 0; k < 3; k++) {
        // Produce two random primes up to the specified bit length
        BigInteger n = BigInteger.ONE;
        int calcBitLength = 0;
        do {
            BigInteger p = BigInteger.probablePrime(bitLength, r);
            BigInteger q = BigInteger.probablePrime(bitLength, r);
            n = p.multiply(q);
            // System.out.println("p="+p+" q="+q+" n="+n);

            // recalculate the bit length based on the produced value of n
            // it could be less than bitLength. We want it to be the same.
            calcBitLength = n.bitLength() / 2;
        } while (calcBitLength != bitLength);

        // calculate the upper value of the search range.
        // max is maximum value for the given bit length. It's likely overly large. Can
        // it be smaller?
        BigInteger max = TWO.pow(calcBitLength + 1).subtract(BigInteger.ONE);
        // System.out.println("bitLength="+calcBitLength+ ", max="+max);

        // attempt to factor n. start at the sqrt of n and work up.
        BigInteger i = n.sqrt();
        // System.out.println("initial i="+i);

        // Try to invoke the garbage collector before cracking so that it is less likely
        // to happen
        // during cracking. Java might listen.
        System.gc();

        ForkJoinPool fjPool = new ForkJoinPool();
        long startTime = System.currentTimeMillis();
        // if you had an idea that the solution was within a certain range of numbers,
        // e.g., close to the sqrt of n, you would want to adjust the search to
        // concentrate on this area
        // rather than creating an initial set of thread tasks that span the entire
        // range from the sqrt of n
        // up to max
        ForkJoinRsaTask forkJoinRsaTask = new ForkJoinRsaTask(n, i, max);
        ForkJoinRsaTask.initCont(); // this really should be done internal to the class
        fjPool.invoke(forkJoinRsaTask);

        long endTime = System.currentTimeMillis();
        // System.out.println("Elapsed time: " + (endTime-startTime) + "ms");
        long elapsedTime = endTime - startTime;
        //System.out.println((bitLength) + " " + (elapsedTime));
 
        if (k == 0) {
            t1 = elapsedTime;
        } else if (k == 1) {
            t2 = elapsedTime;
        } else if (k == 2) {
            t3 = elapsedTime;
            avg = (t1 + t2 + t3) / 3;
            System.out.println((bitLength) + " " + (avg));
        }
    }
    }
}
}

class ForkJoinRsaTask extends RecursiveAction
{
    BigInteger min, n, max;
    private static final BigInteger THRESHOLD = new BigInteger("1000");

    // This variable will tell threads whether they should continue factoring.
    // It must be protected through synchronization because
    // ALL threads may try to access it simultaneously!!!!
    static boolean cont = true;  // should all threads continue?

    // Synchronized methods to protect access to "cont". Only one static
    // synchronized method for a class can be running at a time!!!
    static synchronized boolean getCont() { return cont; }
    static synchronized void initCont() { cont = true; }
    static synchronized void endCont() { cont = false; }

    public ForkJoinRsaTask(BigInteger n, BigInteger min, BigInteger max)
    {
        this.min = min;
        this.n=n;
        this.max=max;
	//initCont();
    }

    @Override
    protected void compute()
    {
        BigInteger diff = max.subtract(min);
	// once the search is sufficiently small, just loop through it
        if (diff.compareTo(THRESHOLD)<0)
	    {
		BigInteger currentPrime = min;

		// loop through each prime less than max and see if any divide n.  If so, print the found value, and do a
		// system exit to end all threads each time through the loop, select the next value to test by using the
		// BigInteger nextProbablePrime method
		do
		    {
			if (n.mod(currentPrime).equals(BigInteger.ZERO))
			    {
				//System.out.println("Keys (p,q): " + currentPrime+", " + n.divide(currentPrime);
				// iF this was a "home run" hack trying to just exit as soon as a factor was found,
				// we could do that right here. Otherwise, we have to coordinate with the threads
				// through the "cont" variable so they know they need to exit. 
				//System.exit(0);
				endCont();
				break;
			    }
			else
			    currentPrime = currentPrime.nextProbablePrime();

		    } while (getCont() && currentPrime.compareTo(max) <= 0);
	    }
        else if (getCont() )
	    {
		//Cut the list in half, and call ForkJoinRsaTask for the two halves (similar to multithreaded quicksort)
		BigInteger mid = (max.add(min)).divide(BigInteger.TWO);
		ForkJoinRsaTask taskA = new ForkJoinRsaTask(n,min,mid);
		ForkJoinRsaTask taskB = new ForkJoinRsaTask(n,mid.add(BigInteger.ONE),max);
		taskA.fork();
		taskB.compute();
		taskA.join();
	    }
    }
}