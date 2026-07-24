package com.robohon.template;

import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;

/**
 * 名前入りハッピーバースデー。
 *
 * <p>純正の歌アプリと同じ構造を再現する: メロディ音源を2分割し、その隙間に宛名を TTS で挟む。
 * <pre>
 *   前半メロディ（「〜ハッピーバースデーディア」まで） → 宛名をTTS発話 → 後半メロディ（締め）
 * </pre>
 * 純正曲を {@code SongUtil} 経由で鳴らすと宛名を差し替えられず常に同じ人の名前になるため、
 * 名前を指定した誕生日の歌はこちらで鳴らす。
 *
 * <p><b>音源の扱い</b>: メロディは端末内蔵ファイルを<b>実行時に読んで再生するだけ</b>で、
 * APK への同梱・リポジトリへの追加・再配布は一切しない（シャープ配布物のため）。
 * 端末にファイルが無い/読めない場合は {@link #isAvailable()} が false を返すので、
 * 呼び出し側は歌わずに言葉でお祝いするなどのフォールバックを取ること。
 *
 * <p>MediaPlayer のコールバックは、この一連の生成をメインスレッドで行う限りメインスレッドへ返る
 * （呼び出し側の状態機械と同じスレッドで扱えるようにするため、必ずメインスレッドから使うこと）。
 */
public final class BirthdaySong {
    private static final String TAG = "BirthdaySong";

    /** 端末内蔵のメロディ音源。前半＝「ディア」まで／後半＝締めの「ハッピーバースデートゥーユー」。 */
    private static final String PART1 = "/vendor/etc/robot/media/1120.mp3";
    private static final String PART2 = "/vendor/etc/robot/media/1121.mp3";

    public interface Listener {
        /** 前半の再生が終わった。ここで宛名を発話する（発話完了後に {@link #playPart2()} を呼ぶ）。 */
        void onPart1Finished();

        /** 歌い終わり、または再生できずに終了した。{@code ok=false} は再生失敗。 */
        void onSongFinished(boolean ok);
    }

    private final Listener mListener;
    private MediaPlayer mPlayer;

    public BirthdaySong(Listener listener) {
        mListener = listener;
    }

    /** 端末にメロディ音源があり読み取れるか（無ければこの機能は使えない）。 */
    public static boolean isAvailable() {
        try {
            return new File(PART1).canRead() && new File(PART2).canRead();
        } catch (Throwable t) {
            Log.w(TAG, "availability check failed: " + t);
            return false;
        }
    }

    /** 前半メロディを再生する。完了で {@link Listener#onPart1Finished()}。 */
    public void playPart1() {
        play(PART1, true);
    }

    /** 後半メロディを再生する。完了で {@link Listener#onSongFinished(boolean)}。 */
    public void playPart2() {
        play(PART2, false);
    }

    private void play(String path, final boolean isPart1) {
        stop();
        MediaPlayer mp = null;
        try {
            mp = new MediaPlayer();
            mp.setDataSource(path);
            mp.setOnCompletionListener(player -> {
                releasePlayer();
                if (isPart1) mListener.onPart1Finished();
                else mListener.onSongFinished(true);
            });
            mp.setOnErrorListener((player, what, extra) -> {
                Log.w(TAG, "MediaPlayer error what=" + what + " extra=" + extra + " path=" + path);
                releasePlayer();
                mListener.onSongFinished(false);
                return true; // エラーは処理済み（onCompletion は呼ばれない）
            });
            mp.prepare();
            mp.start();
            mPlayer = mp;
        } catch (Throwable t) {
            Log.w(TAG, "play failed path=" + path + ": " + t);
            if (mp != null) {
                try {
                    mp.release();
                } catch (Throwable ignore) {
                }
            }
            mPlayer = null;
            mListener.onSongFinished(false);
        }
    }

    /** 再生を止めて解放する（onPause 等。コールバックは呼ばない）。 */
    public void stop() {
        releasePlayer();
    }

    private void releasePlayer() {
        MediaPlayer p = mPlayer;
        mPlayer = null;
        if (p == null) return;
        try {
            p.setOnCompletionListener(null);
            p.setOnErrorListener(null);
            p.release();
        } catch (Throwable ignore) {
        }
    }
}
