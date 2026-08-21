package com.roomsync.booking;

import com.roomsync.location.entity.Location;
import com.roomsync.location.repository.LocationRepository;
import com.roomsync.room.entity.Room;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Category A: Dedicated Pessimistic Lock Test
 * Proves that:
 * Transaction A acquires PESSIMISTIC_WRITE lock on Room
 * -> Transaction B attempts to acquire lock on the same Room and blocks
 * -> Transaction A commits / releases lock
 * -> Transaction B acquires lock and finishes
 */
@SpringBootTest
class PessimisticRoomLockTest {

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long roomId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE bookings, rooms, users, locations RESTART IDENTITY CASCADE");

        Location location = locationRepository.save(Location.builder()
                .name("Mumbai")
                .code("MUM")
                .active(true)
                .build());

        Room room = roomRepository.save(Room.builder()
                .location(location)
                .name("Conference Room Alpha")
                .capacity(12)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build());

        roomId = room.getId();
    }

    @Test
    @DisplayName("Pessimistic Lock: Transaction B must block while Transaction A holds Room lock")
    void testPessimisticRoomLockBlocksConcurrentTransaction() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch txALockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch txACanCommitLatch = new CountDownLatch(1);
        CountDownLatch txBStartedLatch = new CountDownLatch(1);

        AtomicBoolean txBCompleted = new AtomicBoolean(false);

        // Transaction A: Acquires PESSIMISTIC_WRITE lock and holds it until released
        Future<?> txAFuture = executor.submit(() -> {
            TransactionTemplate txTemplateA = new TransactionTemplate(transactionManager);
            txTemplateA.execute(status -> {
                Room lockedRoom = roomRepository.findByIdForUpdate(roomId).orElseThrow();
                assertThat(lockedRoom).isNotNull();

                // Signal that Transaction A acquired the lock
                txALockAcquiredLatch.countDown();

                // Wait until signaled to commit
                try {
                    boolean released = txACanCommitLatch.await(5, TimeUnit.SECONDS);
                    assertThat(released).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        // Ensure Transaction A has acquired the lock before starting Transaction B
        boolean txALocked = txALockAcquiredLatch.await(3, TimeUnit.SECONDS);
        assertThat(txALocked).isTrue();

        // Transaction B: Attempts to acquire PESSIMISTIC_WRITE lock on the same room
        Future<?> txBFuture = executor.submit(() -> {
            txBStartedLatch.countDown();
            TransactionTemplate txTemplateB = new TransactionTemplate(transactionManager);
            txTemplateB.execute(status -> {
                Room lockedRoom = roomRepository.findByIdForUpdate(roomId).orElseThrow();
                assertThat(lockedRoom).isNotNull();
                txBCompleted.set(true);
                return null;
            });
        });

        // Ensure Transaction B has started
        assertThat(txBStartedLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Wait a short duration (500ms) to verify that Transaction B is BLOCKED because Transaction A holds the lock
        Thread.sleep(500);
        assertThat(txBCompleted.get()).isFalse(); // Proves B is blocked!

        // Now signal Transaction A to commit and release the lock
        txACanCommitLatch.countDown();

        // Wait for both transactions to finish
        txAFuture.get(3, TimeUnit.SECONDS);
        txBFuture.get(3, TimeUnit.SECONDS);

        // Transaction B must now have completed successfully after A released the lock
        assertThat(txBCompleted.get()).isTrue();

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }
}
