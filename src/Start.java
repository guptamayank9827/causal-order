import java.io.IOException;
import java.lang.InterruptedException;
import java.util.ArrayList;
import java.util.List;

//Class to start the Process
public class Start {

    public static int NUM_PROCESSES = 4;    //num of total processes
    public static int BASE_PORT = 4000;     //port number for connection

    public static String processIPList[] = {"10.176.69.51", "10.176.69.52", "10.176.69.53", "10.176.69.54"};    //IP addresses of dc20-24 machines
    public static List<ConnectingProcess> connectingProcessList = new ArrayList<ConnectingProcess>();   //connection information to other processes

    public static int processId;    //Id of process running on this machine

    public static void main(String[] args) throws IOException, InterruptedException{

        //initialize variables
        if(args.length > 0) {
            processId = Integer.parseInt(args[0]);
            System.out.println("Process " + processId + " activated!");
        }

        for (int i = 0; i < NUM_PROCESSES; i++) {
            ConnectingProcess connectingProcess = new ConnectingProcess(processIPList[i], BASE_PORT+i);
            connectingProcessList.add(connectingProcess);
        }

        Process process = new Process(processId, NUM_PROCESSES, connectingProcessList);

        //Establish connections with processes on the connectingProcessList
        process.establishConnections();

        //ping at regular intervals to check client & server completion status
        while (true) {
            Thread.sleep(1000);
            if(process.getClientTerminated() && process.getServerTerminated()) {
                break;
            }
        }

        return;
    }
}
