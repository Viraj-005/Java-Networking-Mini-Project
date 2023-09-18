import java.net.*;
import java.util.*;

class Server {
    private static int nextClientId = 1;
    private static Map<Integer, InetSocketAddress> clients = new HashMap<>();

    public static void main(String[] args) throws Exception {
        try (DatagramSocket serverSocket = new DatagramSocket(9876)) {
            byte[] receiveData;
            byte[] sendData;

            System.out.println("Server Started!");

            while (true) {
                receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                String message = new String(receivePacket.getData()).trim();

                if (message.equals("Request client ID")) {
                    int clientId = getNextClientId();
                    InetSocketAddress clientSocketAddress = new InetSocketAddress(clientAddress, clientPort);
                    clients.put(clientId, clientSocketAddress);

                    String idMessage = "Your client ID is " + clientId;
                    sendData = idMessage.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress,
                            clientPort);
                    serverSocket.send(sendPacket);

                    System.out.println("Assigned ID " + clientId + " to client at " + clientSocketAddress);
                } else if (message.startsWith("Request active clients")) {
                    int requesterId = Integer.parseInt(message.split(":")[1]);
                    StringBuilder clientsMessage = new StringBuilder();
                    for (int clientId : clients.keySet()) {
                        if (clientId != requesterId) {
                            clientsMessage.append("Client " + clientId).append("\n");
                        }
                    }
                    sendData = clientsMessage.toString().getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress,
                            clientPort);
                    serverSocket.send(sendPacket);

                    // System.out.println("Sent list of active clients to client at " +
                    // clientAddress + ":" + clientPort);
                } else if (message.startsWith("Disconnect client")) {
                    int clientId = Integer.parseInt(message.split(":")[1]);
                    clients.remove(clientId);
                    System.out.println("Removed ID " + clientId + " for disconnected client at " + clientAddress + ":"
                            + clientPort);
                } else if (message.startsWith("Challenge client")) {
                    int requesterId = Integer.parseInt(message.split(":")[1]);
                    int challengeId = Integer.parseInt(message.split(":")[2]);
                    InetSocketAddress clientSocketAddress = clients.get(challengeId);
                    String idMessage = "Challenged received from:" + requesterId;
                    sendData = idMessage.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                            clientSocketAddress.getAddress(), clientSocketAddress.getPort());
                    serverSocket.send(sendPacket);
                } else if (message.startsWith("Challenge accepted")) {
                    int challengerId = Integer.parseInt(message.split(":")[1]);
                    int acceptorId = Integer.parseInt(message.split(":")[2]);
                    InetSocketAddress challengerSocketAddress = clients.get(challengerId);
                    InetSocketAddress acceptorSocketAddress = clients.get(acceptorId);
                    String idMessage = challengerSocketAddress.getAddress() + "//" + challengerSocketAddress.getPort();
                    sendData = idMessage.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                            acceptorSocketAddress.getAddress(), acceptorSocketAddress.getPort());
                    serverSocket.send(sendPacket);
                    clients.remove(acceptorId);
                    clients.remove(challengerId);
                    System.out.println("Client " + acceptorId + " accepted challenge from " + challengerId);
                } else if (message.startsWith("Client ")) {
                    System.out.println(message);
                }
            }
        }
    }

    private static synchronized int getNextClientId() {
        return nextClientId++;
    }
}