package com.hlebon.server;


import com.hlebon.Constance;
import com.hlebon.message.LoginMessage;
import com.hlebon.message.LogoutMessageServer;
import com.hlebon.message.SayMessage;
import com.hlebon.messageHandlers.server.LoginMessageHandlerServer;
import com.hlebon.messageHandlers.server.LogoutMessageHandlerServer;
import com.hlebon.messageHandlers.server.MessageHandlerServer;
import com.hlebon.messageHandlers.server.SayMessageHandlerServer;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AioTcpServer implements Runnable {
    private AsynchronousChannelGroup asyncChannelGroup;
    private AsynchronousServerSocketChannel listener;
    RouteServiceServer routeServiceServer;


    public AioTcpServer(int port, RouteServiceServer routeServiceServer) throws Exception {
        this.routeServiceServer = routeServiceServer;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        asyncChannelGroup = AsynchronousChannelGroup.withThreadPool(executor);
        listener = AsynchronousServerSocketChannel.open(asyncChannelGroup).bind(new InetSocketAddress(port));
    }

    public void run() {
        try {
            AioAcceptHandler acceptHandler = new AioAcceptHandler(routeServiceServer);
            listener.accept(listener, acceptHandler);
            Logger.info("The Server has started");
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Logger.info("finished server");
        }
    }

    public static void main(String... args) throws Exception {
        SenderServiceServer senderServiceServer = new SenderServiceServer();
        new Thread(senderServiceServer).start();
        Logger.info("The SenderServiceServer has started");

        Map<Class, MessageHandlerServer> messageHandlers = new HashMap<>();
        messageHandlers.put(LoginMessage.class, new LoginMessageHandlerServer(senderServiceServer));
        messageHandlers.put(LogoutMessageServer.class, new LogoutMessageHandlerServer(senderServiceServer));
        messageHandlers.put(SayMessage.class, new SayMessageHandlerServer(senderServiceServer));

        RouteServiceServer routeServiceServer = new RouteServiceServer(messageHandlers);
        new Thread(routeServiceServer).start();
        Logger.info("The RouteServiceServer has started");

        AioTcpServer server = new AioTcpServer(Constance.SERVER_PORT, routeServiceServer);
        new Thread(server).start();
    }
}
