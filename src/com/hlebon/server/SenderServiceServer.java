package com.hlebon.server;


import com.hlebon.message.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;

public class SenderServiceServer implements Runnable {
    private BlockingDeque<MessageWrapper> queue = new LinkedBlockingDeque<>();
    private Map<String, AsynchronousSocketChannel> clients = new ConcurrentHashMap<>();

    public void addClient(String newClient, AsynchronousSocketChannel socket) {
        AnswerLoginMessage answerLoginMessage = new AnswerLoginMessage(new ArrayList<>(clients.keySet()));
        addMessageToSend(new MessageWrapper(answerLoginMessage, socket));

        NewClientMessage newClientMessage = new NewClientMessage(newClient);
        sendToEverybody(newClientMessage);

        clients.put(newClient, socket);
    }

    public void removeClient(AsynchronousSocketChannel socket) {
        for (Map.Entry<String, AsynchronousSocketChannel> entry :
                clients.entrySet()) {
            if (entry.getValue() == socket) {
                String nameClient = entry.getKey();
                System.out.println("The client " + nameClient + " has gone");
                clients.remove(nameClient);
                sendToEverybody(new LogoutMessageClient(nameClient));
                break;
            }
        }

    }

    public void sendMessageByName(String name, Message message) {
        AsynchronousSocketChannel socket = clients.get(name);
        if(socket != null) {
            MessageWrapper messageWrapper = new MessageWrapper(message, socket);
            addMessageToSend(messageWrapper);
        }
    }

    private void sendToEverybody(Message message) {
        MessageWrapper messageWrapper;
        for (AsynchronousSocketChannel socket: clients.values()) {
            messageWrapper = new MessageWrapper(message, socket);
            addMessageToSend(messageWrapper);
        }
    }

    public void addMessageToSend(MessageWrapper messageWrapper) {
        queue.add(messageWrapper);
        synchronized (this) {
            notify();
        }
    }

    @Override
    public void run() {
        while (true) {
            synchronized (this) {
                if (queue.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            MessageWrapper messageWrapper = queue.poll();
            Message message = messageWrapper.getMessage();
            AsynchronousSocketChannel socket = messageWrapper.getFrom();

            try {
                byte[] objectInByte = toByte(message);
                int length = objectInByte.length;

                ByteBuffer byteBuffer = ByteBuffer.allocate(length + 2);
                byteBuffer.put(objectInByte);
                byteBuffer.put((byte)-1);
                byteBuffer.put((byte)-1);
                byteBuffer.rewind();

                System.out.println("Send message " + message);

                do {
                    Future<Integer> future = socket.write(byteBuffer);
                    future.get();
                } while (byteBuffer.position() < byteBuffer.limit());
            } catch (IOException | InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private static byte[] toByte(Message loginMessage) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;

        out = new ObjectOutputStream(bos);
        out.writeObject(loginMessage);
        out.flush();
        return bos.toByteArray();
    }
}