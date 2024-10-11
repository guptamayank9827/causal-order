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

}
