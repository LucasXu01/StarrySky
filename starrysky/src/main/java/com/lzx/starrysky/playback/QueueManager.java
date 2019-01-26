/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lzx.starrysky.playback;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.lzx.starrysky.model.MusicProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;


public class QueueManager {
    private static final String TAG = "QueueManager";

    private MusicProvider mMusicProvider;
    private MetadataUpdateListener mListener;

    //正在播放的队列
    private List<MediaSessionCompat.QueueItem> mPlayingQueue;
    //下标
    private int mCurrentIndex;

    public QueueManager(@NonNull MusicProvider musicProvider,
                        @NonNull MetadataUpdateListener listener) {
        this.mMusicProvider = musicProvider;
        this.mListener = listener;

        mPlayingQueue = Collections.synchronizedList(new ArrayList<>());
        mCurrentIndex = 0;
    }

    /**
     * 判断传入的媒体跟正在播放的媒体是否一样
     */
    public boolean isSameBrowsingCategory(@NonNull String mediaId) {
        MediaSessionCompat.QueueItem current = getCurrentMusic();
        if (current == null) {
            return false;
        }
        return mediaId.equals(current.getDescription().getMediaId());
    }

    /**
     * 更新当前下标并通知
     */
    private void setCurrentQueueIndex(int index) {
        if (index >= 0 && index < mPlayingQueue.size()) {
            mCurrentIndex = index;
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex);
        }
    }

    /**
     * 根据传入的媒体id来更新此媒体的下标并通知
     */
    public boolean setCurrentQueueItem(String mediaId) {
        int index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    /**
     * 转跳下一首或上一首
     *
     * @param amount 正为下一首，负为上一首
     */
    public boolean skipQueuePosition(int amount) {
        if (mPlayingQueue.size() == 0) {
            return false;
        }
        int index = mCurrentIndex + amount;
        if (index < 0) {
            index = 0;
        } else {
            index %= mPlayingQueue.size();
        }
        if (!QueueHelper.isIndexPlayable(index, mPlayingQueue)) {
            return false;
        }
        mCurrentIndex = index;
        return true;
    }

    /**
     * 打乱当前的列表顺序
     */
    public void setRandomQueue() {
        setCurrentQueue(QueueHelper.getRandomQueue(mMusicProvider));
        updateMetadata();
    }

    public void setQueueFromMusic(String mediaId) {
        boolean canReuseQueue = false;
        if (isSameBrowsingCategory(mediaId)) {
            canReuseQueue = setCurrentQueueItem(mediaId);
        }
        if (!canReuseQueue) {
            setCurrentQueue(QueueHelper.getPlayingQueue(mMusicProvider), mediaId);
        }
        updateMetadata();
    }

    /**
     * 获取当前播放的媒体
     */
    public MediaSessionCompat.QueueItem getCurrentMusic() {
        if (!QueueHelper.isIndexPlayable(mCurrentIndex, mPlayingQueue)) {
            return null;
        }
        return mPlayingQueue.get(mCurrentIndex);
    }

    /**
     * 获取队列大小
     */
    public int getCurrentQueueSize() {
        if (mPlayingQueue == null) {
            return 0;
        }
        return mPlayingQueue.size();
    }

    /**
     * 更新队列,下标为 0
     */
    protected void setCurrentQueue(List<MediaSessionCompat.QueueItem> newQueue) {
        setCurrentQueue(newQueue, null);
    }

    /**
     * 更新队列和下标
     */
    protected void setCurrentQueue(List<MediaSessionCompat.QueueItem> newQueue, String initialMediaId) {
        mPlayingQueue = newQueue;
        int index = 0;
        if (initialMediaId != null) {
            index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, initialMediaId);
        }
        mCurrentIndex = Math.max(index, 0);
        mListener.onQueueUpdated(newQueue);
    }

    /**
     * 更新媒体信息
     */
    public void updateMetadata() {
        MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();
        if (currentMusic == null) {
            mListener.onMetadataRetrieveError();
            return;
        }
        final String musicId = currentMusic.getDescription().getMediaId();
        MediaMetadataCompat metadata = mMusicProvider.getMusic(musicId);
        if (metadata == null) {
            throw new IllegalArgumentException("Invalid musicId " + musicId);
        }
        mListener.onMetadataChanged(metadata);
    }

    public interface MetadataUpdateListener {
        void onMetadataChanged(MediaMetadataCompat metadata);

        void onMetadataRetrieveError();

        void onCurrentQueueIndexUpdated(int queueIndex);

        void onQueueUpdated(List<MediaSessionCompat.QueueItem> newQueue);
    }
}
