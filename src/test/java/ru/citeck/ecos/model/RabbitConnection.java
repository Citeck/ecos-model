package ru.citeck.ecos.model;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.connection.SimpleConnection;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class RabbitConnection implements org.springframework.amqp.rabbit.connection.ConnectionFactory,
    RabbitMqConnProvider {

    private final ConnectionFactory impl = new MockConnectionFactory();

    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private RabbitMqConn rabbitMqConn;

    @Override
    public Connection createConnection() throws AmqpException {
        try {
            Connection conn = new SimpleConnection(impl.newConnection(), 10);
            connections.add(conn);
            return conn;
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public RabbitMqConn getConnection() {
        if (rabbitMqConn == null) {
            rabbitMqConn = new RabbitMqConn(impl);
        }
        return rabbitMqConn;
    }

    @Override
    public String getHost() {
        return impl.getHost();
    }

    @Override
    public int getPort() {
        return impl.getPort();
    }

    @Override
    public String getVirtualHost() {
        return impl.getVirtualHost();
    }

    @Override
    public String getUsername() {
        return impl.getUsername();
    }

    @Override
    public void addConnectionListener(ConnectionListener connectionListener) {
    }

    @Override
    public boolean removeConnectionListener(ConnectionListener connectionListener) {
        return false;
    }

    @Override
    public void clearConnectionListeners() {
    }

    @EventListener
    public void preDestroy(ContextStoppedEvent event) {
        log.info("Close all connections");
        if (rabbitMqConn != null) {
            try {
                rabbitMqConn.close();
            } catch (Exception e) {
                //do nothing
            }
        }
        for (Connection conn : connections) {
            try {
                if (conn.isOpen()) {
                    conn.close();
                }
            } catch (Exception e) {
                //do nothing
            }
        }
    }
}
