package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.ChatMessageEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * ChatMessageDao
 *
 * Room DAO for all read and write operations against the {@code chat_messages} table.
 *
 * Architecture layer : Data / Local
 * Table              : chat_messages
 * Entity             : {@link ChatMessageEntity}
 */
@Dao
public interface ChatMessageDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single chat message.
     *
     * @param message The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(ChatMessageEntity message);

    /**
     * Inserts a list of messages.
     *
     * @param messages List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<ChatMessageEntity> messages);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Thread & Session
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observes all messages in a specific conversation thread, oldest first.
     *
     * @param userId    Local PK of the owner.
     * @param sessionId UUID of the conversation thread.
     * @return {@link LiveData} containing the list of messages in the thread.
     */
    @Query("SELECT * FROM chat_messages " +
            "WHERE user_id = :userId AND session_id = :sessionId " +
            "ORDER BY timestamp ASC")
    LiveData<List<ChatMessageEntity>> observeThread(long userId, String sessionId);

    /**
     * Fetches a snapshot of all messages in a thread.
     *
     * @param userId    Local PK of the owner.
     * @param sessionId UUID of the conversation thread.
     * @return {@link Single} emitting the list of messages.
     */
    @Query("SELECT * FROM chat_messages " +
            "WHERE user_id = :userId AND session_id = :sessionId " +
            "ORDER BY timestamp ASC")
    Single<List<ChatMessageEntity>> getThreadSnapshot(long userId, String sessionId);

    /**
     * Observes the list of unique conversation sessions for a user.
     * Returns the session ID and the timestamp of the latest message in that session.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} emitting a list of {@link SessionResult}.
     */
    @Query("SELECT session_id, MAX(timestamp) AS lastActive, content AS lastMessageContent " +
            "FROM chat_messages " +
            "WHERE user_id = :userId " +
            "GROUP BY session_id " +
            "ORDER BY lastActive DESC")
    LiveData<List<SessionResult>> observeSessionList(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Analytics & Budget
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Counts total tokens consumed by a user across all sessions.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting total token count.
     */
    @Query("SELECT SUM(token_count) FROM chat_messages WHERE user_id = :userId AND token_count IS NOT NULL")
    Single<Long> getTotalTokenUsage(long userId);

    /**
     * Searches for messages containing specific text.
     *
     * @param userId Local PK of the owner.
     * @param query  The search string (use %query% for partial matching).
     * @return {@link Single} emitting matching messages.
     */
    @Query("SELECT * FROM chat_messages WHERE user_id = :userId AND content LIKE :query ORDER BY timestamp DESC")
    Single<List<ChatMessageEntity>> searchMessages(long userId, String query);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing message (e.g. for feedback ratings or retries).
     *
     * @param message The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(ChatMessageEntity message);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific message.
     *
     * @param message The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(ChatMessageEntity message);

    /**
     * Deletes an entire conversation thread.
     *
     * @param userId    Local PK of the owner.
     * @param sessionId UUID of the conversation thread to remove.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM chat_messages WHERE user_id = :userId AND session_id = :sessionId")
    Completable deleteThread(long userId, String sessionId);

    /**
     * Deletes all chat history for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM chat_messages WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Projection POJO — SessionResult
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Non-entity projection returned by {@link #observeSessionList}.
     */
    class SessionResult {
        public String session_id;
        public long lastActive;
        public String lastMessageContent;
    }
}
