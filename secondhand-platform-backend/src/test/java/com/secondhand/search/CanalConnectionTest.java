package com.secondhand.search;

import com.alibaba.otter.canal.protocol.CanalPacket.*;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.google.protobuf.ByteString;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.secondhand.config.CanalSyncConfig;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用本机临时 TCP 服务验证真实 Canal 客户端的握手、订阅、取消息与确认。 */
class CanalConnectionTest {
    @Test @Timeout(15)
    void actualTcpClientUsesCorrectCredentialsAndDestinationAndAcknowledgesAfterDelivery() throws Exception {
        // 先在测试线程加载实际客户端，避免后台线程的缺失依赖被 Future 隐藏。
        new com.alibaba.otter.canal.client.impl.SimpleCanalConnector(new InetSocketAddress("127.0.0.1", 1), "", "", "campus");
        ProductSearchSyncWriter writer = mock(ProductSearchSyncWriter.class);
        ExecutorService serverThread = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(5000);
            Future<?> protocol = serverThread.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    send(out, Packet.newBuilder().setVersion(1).setType(PacketType.HANDSHAKE)
                            .setBody(Handshake.newBuilder().setSeeds(ByteString.copyFromUtf8("12345678901234567890")).build().toByteString()).build());
                    Packet authPacket = read(in);
                    assertThat(authPacket.getType()).isEqualTo(PacketType.CLIENTAUTHENTICATION);
                    assertThat(ClientAuth.parseFrom(authPacket.getBody()).getUsername()).isEmpty();
                    send(out, Packet.newBuilder().setType(PacketType.ACK).setBody(Ack.getDefaultInstance().toByteString()).build());
                    assertThat(read(in).getType()).isEqualTo(PacketType.CLIENTROLLBACK);
                    Sub subscription = Sub.parseFrom(read(in).getBody());
                    assertThat(subscription.getDestination()).isEqualTo("campus");
                    assertThat(subscription.getFilter()).isEqualTo("secondhand\\.(product|product_image|category)");
                    send(out, Packet.newBuilder().setType(PacketType.ACK).setBody(Ack.getDefaultInstance().toByteString()).build());
                    assertThat(read(in).getType()).isEqualTo(PacketType.CLIENTROLLBACK);
                    Packet get = read(in);
                    assertThat(get.getType()).isEqualTo(PacketType.GET);
                    assertThat(Get.parseFrom(get.getBody()).getDestination()).isEqualTo("campus");
                    var entry = CanalSyncTest.entry("secondhand", "product", EventType.INSERT,
                            RowData.newBuilder().addAfterColumns(CanalSyncTest.column("id", "1")).build());
                    send(out, Packet.newBuilder().setType(PacketType.MESSAGES).setBody(Messages.newBuilder()
                            .setBatchId(12).addMessages(entry.toByteString()).build().toByteString()).build());
                    Packet ack = read(in);
                    assertThat(ack.getType()).isEqualTo(PacketType.CLIENTACK);
                    assertThat(ClientAck.parseFrom(ack.getBody()).getBatchId()).isEqualTo(12);
                    verify(writer).apply(any());
                } catch (IOException ex) { throw new UncheckedIOException(ex); }
            });
            CanalSyncProperties p = new CanalSyncProperties(); p.setPort(server.getLocalPort());
            CanalSyncWorker worker = new CanalSyncWorker(p, new CanalBatchProcessor(new CanalChangeDecoder("secondhand"), writer, 100));
            try { worker.start(); protocol.get(8, TimeUnit.SECONDS); }
            finally { worker.destroy(); }
        } finally { serverThread.shutdownNow(); }
    }

    @Test void disabledFeatureRegistersNoWorkerAndInvalidCombinationFailsClearly() {
        new ApplicationContextRunner().withUserConfiguration(CanalSyncConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(CanalSyncWorker.class));
        new ApplicationContextRunner().withUserConfiguration(CanalSyncConfig.class)
                .withPropertyValues("app.search.canal.enabled=true", "app.search.elasticsearch.enabled=false")
                .run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage("启用 Canal 同步必须同时启用 ES 搜索"));
    }

    private static Packet read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 1024 * 1024) throw new IOException("invalid test packet size");
        return Packet.parseFrom(in.readNBytes(length));
    }
    private static void send(DataOutputStream out, Packet packet) throws IOException {
        byte[] bytes = packet.toByteArray(); out.writeInt(bytes.length); out.write(bytes); out.flush();
    }
}
