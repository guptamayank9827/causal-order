import java.io.Serializable;

//class to store the messages transfered
public class Message implements Serializable {
    
    String type;    //Eg: INTRODUCTION, ACKNOWLEDGEMENT, CONNECTIONS, START, APPLICATION
    int senderProcessId;    //process ID of sender
    int receiverProcessId;  //process ID of receiver
    int[] clock;    //timestamp of message sender
    int mid;    //message ID

    //constructor
    public Message(String type, int senderProcessId, int receiverProcessId, int[] clock, int id) {
        this.type = type;
        this.senderProcessId = senderProcessId;
        this.receiverProcessId = receiverProcessId;
        this.clock = clock;
        this.mid = id;
    }

    /*@Override
    public int compareTo(Message other) {
        // Compare based on the vectortimestamp
        if (this.vectortimestamp[from] < other.vectortimestamp[from]) {
            return -1;
        } else if (this.vectortimestamp[from] > other.vectortimestamp[from]) {
            return 1;
        } else {
            int l = 0;
            int g = 0;
            for (int i = 0; i < vectortimestamp.length; i++) {
                if (this.vectortimestamp[i] != other.vectortimestamp[i]) {
                    if (this.vectortimestamp[i] > other.vectortimestamp[i]) {
                        l = l + 1;
                    } else {
                        g = g + 1;
                    }
                }
            }
            if ((l == 0 && g == 0) || (l > 0 && g > 0)) {
                return 0;
            } else if (l > 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }*/

}
