import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

//Class to store the TimeStamps using VectorClocks
public class VectorClock {
 
    int processId;  //holds the timestamp of this process
    int clockIncrement; //value of increment on each local event
    int clock[]; //array of timestamps of each process
    private final ReadWriteLock timeStampLock = new ReentrantReadWriteLock();   //lock to keep timestamp consistent

    //constructor
    public VectorClock(int size, int processId) {
        this.clockIncrement = 1;    //default increment value
        this.clock = new int[size]; //size=NUM_PROCESSES
        this.processId = processId;
    }

    public int[] getVectorClock() {
        this.timeStampLock.readLock().lock();
        try {
            // give a cloned copy for immutability
            return this.clock.clone();
        }
        finally {
            this.timeStampLock.readLock().unlock();
        }
    }

    //update timestamp of a local event
    public synchronized void updateLocalClock(int processId) {
        this.timeStampLock.writeLock().lock();
        try {
            //update only the process' time
            this.clock[processId] += this.clockIncrement;

        } finally {
            this.timeStampLock.writeLock().unlock();
        }
    }

    //update timestamp when delivering a message
    public synchronized void updateClock(Message message) {
        this.timeStampLock.writeLock().lock();
        try {
            //component-wise maximum of each process' timestamp
            for (int i = 0; i < this.clock.length; i++) {
                //update local timestamp of process
                int localTimeStamp = i == processId ? (this.clock[i]+this.clockIncrement) : this.clock[i];
                int messageTimeStamp = message.clock[i];

                this.clock[i] = Math.max(localTimeStamp, messageTimeStamp);
            }

        }
        catch(Exception e) {
            e.printStackTrace();
        }
        finally {
            this.timeStampLock.writeLock().unlock();
        }
        return;
    }

    //helper function to put read-write locks on timestamp
    public synchronized void lockUnlockTime(String type, String method) {
        if(type == "read") {
            if(method == "lock") { this.timeStampLock.readLock().lock(); }
            else if(method == "unlock") { this.timeStampLock.readLock().unlock(); }
        }
        else if(type == "write") {
            if(method == "lock") { this.timeStampLock.writeLock().lock(); }
            else if(method == "unlock") { this.timeStampLock.writeLock().unlock(); }
        }
    }
}
