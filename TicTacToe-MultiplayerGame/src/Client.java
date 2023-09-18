import java.io.IOException;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

class Person {
    private int ID;

    Person() {
        this.ID = -1;
    }

    Person(int id) {
        this.ID = id;
    }

    public void setID(int id) {
        this.ID = id;
    }

    public int getID() {
        return this.ID;
    }
}

class Connection {
    private static Person p;
    private static DatagramPacket packet;
    private static DatagramSocket socket;
    private static InetSocketAddress challengerSocketAddress = null;

    Connection() {
        p = new Person();
    }

    public void initializeConnection() throws Exception {
        socket = new DatagramSocket();
        socket.setSoTimeout(2000);
        byte[] buffer = new byte[1024];
        packet = new DatagramPacket(buffer, buffer.length);
        packet.setAddress(InetAddress.getByName("localhost"));
        packet.setPort(9876);
    }

    public int getClientID() throws IOException {
        String message = "Request client ID";
        messageSender(message);

        // receive client ID
        message = messageReceiver();
        int clientId = Integer.parseInt(message.split(" ")[4]);
        p.setID(clientId);
        System.out.println("Received client ID: " + clientId);
        return clientId;
    }

    public String[] getActiveClientsList() throws IOException {
        String message = "Request active clients:" + p.getID();
        messageSender(message);
        message = messageReceiver();
        String[] clientIds = message.split("\n");
        // System.out.println("Active clients:\n" + String.join("\n", clientIds));
        return clientIds;
    }

    public int getPersonId() {
        return p.getID();
    }

    public void disconnectClient() throws Exception {
        String message = "Disconnect client:" + p.getID();
        messageSender(message);
        socket.close();
    }

    public void closeConnection() {
        socket.close();
    }

    public void challengeClient(int clientId) throws IOException {
        String message = "Challenge client:" + p.getID() + ":" + clientId;
        messageSender(message);
    }

    public void acceptChallenge(int challengerId) throws Exception {
        messageSender("Challenge accepted:" + challengerId + ":" + p.getID());
        String message = messageReceiver();
        InetAddress challengerAddress = InetAddress.getByName(message.split("//")[0].replace("/", ""));
        int challengerPort = Integer.parseInt(message.split("//")[1]);
        challengerSocketAddress = new InetSocketAddress(challengerAddress, challengerPort);
        this.transferP2PMessage("Initialize new connection" + "/127.0.0.1" + "//" + socket.getLocalPort());
    }

    public String messageReceiver() throws IOException {
        byte[] receiveData = new byte[1024];
        String message;
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        message = new String(receivePacket.getData()).trim();
        return message;
    }

    public void messageSender(String message) throws IOException {
        byte[] request;
        request = message.getBytes();
        packet.setData(request);
        packet.setLength(request.length);
        socket.send(packet);
    }

    public void setChallengerSocketAddress(InetSocketAddress socketAddress) {
        challengerSocketAddress = socketAddress;
    }

    public InetSocketAddress getChallengerSocketAddress() {
        return challengerSocketAddress;
    }

    public void transferP2PMessage(String message) throws Exception {
        // InetSocketAddress ownAddress = new InetSocketAddress("localhost", 9877);
        // socket.bind(ownAddress);
        // System.out.println("Establishing connection with client at port " +
        // peerAddress.getPort());
        byte[] buffer = message.getBytes();
        packet = new DatagramPacket(buffer, buffer.length, challengerSocketAddress);
        socket.send(packet);
        // socket.close();
    }
}

class gameUI {
    private JFrame frame;
    private JList<String> clientList;
    private String challengedClientId;
    private Connection client;
    JButton[] buttons;
    JLabel titleLabel, turnLabel;
    private boolean playerTurn;

    public gameUI() {
        client = new Connection();
    }

    public void messageParser(String Message) throws Exception {
        if (Message.startsWith("Challenged received from")) {
            int challengerId = Integer.parseInt(Message.split(":")[1]);
            if (displayChallengeAcceptor(challengerId)) {
                client.acceptChallenge(challengerId);
            }
        } else if (Message.startsWith("Initialize new connection")) {
            InetAddress receiverAddress = InetAddress.getByName(Message.split("/")[1].split("//")[0]);
            int receiverPort = Integer.parseInt(Message.split("//")[1]);
            client.setChallengerSocketAddress(new InetSocketAddress(receiverAddress, receiverPort));
            client.transferP2PMessage("Connection established");
            frame.dispose();
            this.displayGrid();
        } else if (Message.startsWith("Connection established")) {
            frame.dispose();
            this.displayGrid();
        } else if (Message.startsWith("O") || Message.startsWith("X")) {
            String selectedTile = Message.split(":")[0];
            int index = Integer.parseInt(Message.split(":")[1]);
            buttons[index].setText(selectedTile);
            if (selectedTile.equals("X")) {
                turnLabel.setText("Player 2's turn");
            } else {
                turnLabel.setText("Player 1's turn");
            }
            checkWin(selectedTile);
            setPlayerTurn(true);
        }
    }

    public void displayClientList() throws Exception {
        client.initializeConnection();
        frame = new JFrame(String.valueOf("Client" + client.getClientID()));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(new Color(50, 50, 50));
        frame.setSize(550, 380);

        clientList = new JList<>();
        clientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clientList.setForeground(Color.BLACK);

        // Initialize the button and set its action listener
        JButton button = new JButton("Send Challenge");
        button.setForeground(Color.WHITE);
        button.setBackground(Color.BLUE);
        button.setFont(new Font("Fira Sans ExtraBold", Font.BOLD, 25));

        JLabel label1 = new JLabel("Challenge sent!");
        label1.setForeground(Color.GREEN);
        // label1.setFont(new Font("Segoe Print", Font.BOLD, 14));

        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                challengedClientId = clientList.getSelectedValue();
                if (challengedClientId != null) {
                    challengedClientId = challengedClientId.split(" ")[1];
                    try {
                        client.challengeClient(Integer.parseInt(challengedClientId));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                label1.setVisible(true);
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                try {
                    client.disconnectClient();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    if (client.getChallengerSocketAddress() == null) {
                        String[] updatedClients = client.getActiveClientsList();
                        if (clientList.getModel().getSize() != updatedClients.length
                                || clientList.getModel().getSize() == 1) {
                            clientList.setListData(updatedClients);
                            clientList.revalidate();
                            clientList.repaint();
                        }
                        label1.setVisible(false);
                    }
                    String message = client.messageReceiver();
                    if (message.length() > 0)
                        messageParser(message);
                } catch (Exception e) {

                }
            }
        };
        timer.schedule(task, 0, 2000);

        // Add the list and button to the frame
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(clientList), BorderLayout.CENTER);
        frame.add(button, BorderLayout.SOUTH);
        frame.add(label1, BorderLayout.NORTH);

        // Display the frame
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.setResizable(false);
    }

    public void displayGrid() {
        JFrame frame1 = new JFrame("Client " + client.getPersonId() + " Tic Tac Toe");
        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame1.getContentPane().setBackground(new Color(50, 50, 50));
        frame1.setSize(700, 700);

        // Create the labels
        titleLabel = new JLabel("Tic Tac Toe", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Montserrat Medium", Font.BOLD, 40));
        titleLabel.setForeground(new Color(255, 255, 0));

        turnLabel = new JLabel("Player 1's turn", SwingConstants.CENTER);
        turnLabel.setFont(new Font("Montserrat Medium", Font.BOLD, 20));
        turnLabel.setForeground(Color.WHITE);

        buttons = new JButton[9];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new JButton("");
            buttons[i].addActionListener(new ButtonListener(i));
            buttons[i].setFont(new Font("MV Boli", Font.BOLD, 120));
        }

        frame1.add(titleLabel, BorderLayout.NORTH);
        JPanel buttonPanel = new JPanel(new GridLayout(3, 3));
        buttonPanel.setBackground(new Color(150, 150, 150));
        for (int i = 0; i < buttons.length; i++) {
            buttonPanel.add(buttons[i]);
        }

        frame1.add(buttonPanel, BorderLayout.CENTER);
        frame1.add(turnLabel, BorderLayout.SOUTH);

        // Initialize the game
        playerTurn = true;
        frame1.setLocationRelativeTo(null);
        frame1.setVisible(true);
    }

    public void setPlayerTurn(boolean turn) {
        playerTurn = turn;
    }

    public void takeTurn(int index) throws Exception {
        if (turnLabel.getText().equals("Player 1's turn")) {
            buttons[index].setText("X");
            buttons[index].setForeground(new Color(255, 0, 0));
            client.transferP2PMessage("X:" + index);
            checkWin("X");
            turnLabel.setText("Player 2's turn");
        } else {
            buttons[index].setText("O");
            buttons[index].setForeground(new Color(0, 0, 255));
            client.transferP2PMessage("O:" + index);
            checkWin("O");
            turnLabel.setText("Player 1's turn");
        }
        setPlayerTurn(false);
    }

    private void checkWin(String player) throws Exception {
        // Check for horizontal win
        for (int i = 0; i < 9; i += 3) {
            if (buttons[i].getText().equals(player) && buttons[i + 1].getText().equals(player)
                    && buttons[i + 2].getText().equals(player)) {
                win(player);
                return;
            }
        }
        // Check for vertical win
        for (int i = 0; i < 3; i++) {
            if (buttons[i].getText().equals(player) && buttons[i + 3].getText().equals(player)
                    && buttons[i + 6].getText().equals(player)) {
                win(player);
                return;
            }
        }
        // Check for diagonal win
        if (buttons[0].getText().equals(player) && buttons[4].getText().equals(player)
                && buttons[8].getText().equals(player)) {
            win(player);
            return;
        }
        if (buttons[2].getText().equals(player) && buttons[4].getText().equals(player)
                && buttons[6].getText().equals(player)) {
            win(player);
            return;
        }

        // Check for draw
        boolean draw = true;
        for (int i = 0; i < 9; i++) {
            if (buttons[i].getText().equals("")) {
                draw = false;
                break;
            }
        }
        if (draw) {
            draw();
        }
    }

    private void win(String player) throws Exception {
        client.initializeConnection();
        for (int i = 0; i < 9; i++) {
            buttons[i].setEnabled(false);
            buttons[i].setBackground(Color.GREEN);
        }

        if (player.equals("X")) {
            // turnLabel.setText("Client" + client.getPersonId() + "wins!");
            JOptionPane.showMessageDialog(frame, "Player 1 wins!", "Winner", JOptionPane.INFORMATION_MESSAGE);
        } else if (player.equals("O")) {
            // turnLabel.setText("Client" + client.getPersonId() + "wins!");
            JOptionPane.showMessageDialog(frame, "Player 2 wins!", "Winner", JOptionPane.INFORMATION_MESSAGE);
        }

        if (playerTurn)
            client.messageSender("Client " + client.getPersonId() + " won!");
        client.closeConnection();
    }

    private void draw() throws Exception {
        client.initializeConnection();
        for (int i = 0; i < 9; i++) {
            buttons[i].setEnabled(false);
            buttons[i].setBackground(Color.RED);
        }
        JOptionPane.showMessageDialog(frame, "It's a draw!", "Draw", JOptionPane.INFORMATION_MESSAGE);
        if (playerTurn)
            client.messageSender(
                    "Client " + client.getPersonId() + " and Client " + challengedClientId + " match drawn!");
        client.closeConnection();
    }

    private class ButtonListener implements ActionListener {
        private int index;

        public ButtonListener(int index) {
            this.index = index;
        }

        public void actionPerformed(ActionEvent e) {
            if (buttons[index].getText().equals("")) {
                if (playerTurn) {
                    try {
                        takeTurn(index);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    private boolean displayChallengeAcceptor(int challengerID) throws InterruptedException {
        JFrame challengeFrame = new JFrame("Accept Challenge");
        challengeFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        challengeFrame.setSize(350, 250);
        challengeFrame.setLocationRelativeTo(null);
        challengeFrame.setResizable(false);
        challengeFrame.setLayout(new BorderLayout());

        JLabel label = new JLabel("Accept Challenge From Client " + challengerID, SwingConstants.CENTER);
        label.setFont(new Font("Montserrat Medium", Font.PLAIN, 14));
        challengeFrame.add(label, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        JButton yesButton = new JButton("Yes");
        yesButton.setFont(new Font("Fira Sans ExtraBold", Font.BOLD, 14));
        yesButton.setBackground(Color.GREEN);

        JButton noButton = new JButton("No");
        noButton.setFont(new Font("Fira Sans ExtraBold", Font.BOLD, 14));
        noButton.setBackground(Color.RED);

        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);
        challengeFrame.add(buttonPanel, BorderLayout.SOUTH);
        AtomicBoolean acceptFlag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        yesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acceptFlag.set(true);
                latch.countDown();
                challengeFrame.dispose(); // Close the frame
            }
        });

        noButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acceptFlag.set(false);
                latch.countDown();
                challengeFrame.dispose(); // Close the frame
            }
        });

        challengeFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
                acceptFlag.set(false);
                latch.countDown();
            }
        });

        // Display the frame
        challengeFrame.setVisible(true);
        latch.await(); // Wait for the user to click on either the "Yes" or "No" button
        return acceptFlag.get();
    }
}

public class Client {

    public static void main(String[] args) throws Exception {
        gameUI g = new gameUI();
        g.displayClientList();

        // c.disconnectClient();
    }
}