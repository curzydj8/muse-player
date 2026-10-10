package com.muse.localplayer;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 极简本地播放器：打开即播手机里的歌，海报 + 三按钮 + .smi 歌词，无需网络 */
public class MainActivity extends Activity {

    private static final int REQ_PERM = 1001;

    private TextView tvTitle, tvArtist, tvPoster, tvLrcStatus;
    private ImageView ivPoster;
    private ImageButton btnPlay, btnNext, btnPrev;
    private ListView lvLyrics, lvPlaylist;
    private LinearLayout lyricsPanel;
    private PlaylistAdapter playlistAdapter;

    private MediaPlayer player;
    private List<Song> playlist = new ArrayList<Song>();
    private int index = -1;

    private SmiParser smi = new SmiParser();
    private LyricAdapter lyricAdapter;
    private int curLrcIdx = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable lrcTick = new Runnable() {
        @Override public void run() {
            try {
                if (player != null && player.isPlaying() && smi.size() > 0) {
                    int idx = smi.indexAt(player.getCurrentPosition());
                    if (idx != curLrcIdx) {
                        curLrcIdx = idx;
                        lyricAdapter.notifyDataSetChanged();
                        if (idx >= 0) lvLyrics.smoothScrollToPosition(idx);
                    }
                }
            } catch (Exception ignored) {}
            handler.postDelayed(this, 500);
        }
    };

    static class Song {
        long id; String title; String artist; Uri uri; String path;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        tvPoster = findViewById(R.id.tv_poster);
        ivPoster = findViewById(R.id.iv_poster);
        tvTitle = findViewById(R.id.tv_p_title);
        tvArtist = findViewById(R.id.tv_p_artist);
        tvLrcStatus = findViewById(R.id.tv_lrc_status);
        lvLyrics = findViewById(R.id.lv_lyrics);
        lvPlaylist = findViewById(R.id.lv_playlist);
        lyricsPanel = findViewById(R.id.lyrics_panel);
        btnPlay = findViewById(R.id.btn_play);
        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);

        lyricAdapter = new LyricAdapter();
        lvLyrics.setAdapter(lyricAdapter);
        playlistAdapter = new PlaylistAdapter();
        lvPlaylist.setAdapter(playlistAdapter);

        lvPlaylist.setOnItemClickListener((p, v, pos, id) -> playAt(pos));

        // 切换 播放列表 / 歌词
        android.widget.Button tabPlaylist = findViewById(R.id.tab_playlist);
        android.widget.Button tabLyrics = findViewById(R.id.tab_lyrics);
        tabPlaylist.setOnClickListener(v -> {
            lvPlaylist.setVisibility(View.VISIBLE);
            lyricsPanel.setVisibility(View.GONE);
        });
        tabLyrics.setOnClickListener(v -> {
            lvPlaylist.setVisibility(View.GONE);
            lyricsPanel.setVisibility(View.VISIBLE);
        });

        btnPlay.setOnClickListener(v -> toggle());
        btnNext.setOnClickListener(v -> next());
        btnPrev.setOnClickListener(v -> prev());

        initPlayer();
        handler.post(lrcTick);

        // Android 13+ 用 READ_MEDIA_AUDIO，旧版用 READ_EXTERNAL_STORAGE
        String perm = android.os.Build.VERSION.SDK_INT >= 33
                ? android.Manifest.permission.READ_MEDIA_AUDIO
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, REQ_PERM);
        } else {
            loadLocal();
        }
    }

    private String audioPerm() {
        return android.os.Build.VERSION.SDK_INT >= 33
                ? android.Manifest.permission.READ_MEDIA_AUDIO
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        if (rc == REQ_PERM && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            loadLocal();
        } else {
            tvTitle.setText("需要存储权限");
            tvArtist.setText("请在设置中允许读取音频");
        }
    }

    private void initPlayer() {
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        player.setOnPreparedListener(mp -> { mp.start(); updatePlayBtn(); });
        player.setOnCompletionListener(mp -> next());
        player.setOnErrorListener((mp, what, extra) -> { next(); return true; });
    }

    private void loadLocal() {
        new Thread(() -> {
            List<Song> list = new ArrayList<Song>();
            try {
                Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String[] proj = { MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA };
                String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND "
                        + MediaStore.Audio.Media.DURATION + ">30000";
                Cursor c = getContentResolver().query(uri, proj, sel, null,
                        MediaStore.Audio.Media.TITLE + " ASC");
                if (c != null) {
                    while (c.moveToNext()) {
                        Song s = new Song();
                        s.id = c.getLong(0);
                        s.title = c.getString(1);
                        s.artist = c.getString(2);
                        if (s.artist == null || s.artist.equals("<unknown>")) s.artist = "未知艺术家";
                        s.uri = Uri.withAppendedPath(uri, String.valueOf(s.id));
                        try { s.path = c.getString(4); } catch (Exception ignored) {}
                        list.add(s);
                    }
                    c.close();
                }
            } catch (Exception ignored) {}
            final List<Song> result = list;
            runOnUiThread(() -> {
                playlist = result;
                playlistAdapter.notifyDataSetChanged();
                if (playlist.isEmpty()) {
                    tvTitle.setText("手机里没有音乐");
                    tvArtist.setText("先放几首歌到手机上");
                } else {
                    Toast.makeText(this, "找到 " + playlist.size() + " 首", Toast.LENGTH_SHORT).show();
                    // 随机选一首开始
                    int start = new java.util.Random().nextInt(playlist.size());
                    playAt(start);
                }
            });
        }).start();
    }

    private void playAt(int i) {
        playAt(i, false);
    }

    /** @param fromPrev 是否来自"上一首"按钮：是则不重排列表 */
    private void playAt(int i, boolean fromPrev) {
        if (i < 0 || i >= playlist.size()) return;
        if (!fromPrev && i != 0) {
            // 把当前歌曲转到列表第一位，其余保持相对顺序
            List<Song> rotated = new ArrayList<Song>();
            for (int k = 0; k < playlist.size(); k++) {
                rotated.add(playlist.get((i + k) % playlist.size()));
            }
            playlist = rotated;
            i = 0;
        }
        index = i;
        Song s = playlist.get(i);
        tvTitle.setText(s.title);
        tvArtist.setText(s.artist);
        playlistAdapter.notifyDataSetChanged();
        lvPlaylist.smoothScrollToPosition(0);
        // 找同名 .smi 歌词
        loadSmi(s.path);
        // 加载内嵌封面
        loadCover(s.uri);
        try {
            player.reset();
            player.setDataSource(this, s.uri);
            player.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "无法播放", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCover(Uri uri) {
        new Thread(() -> {
            byte[] art = null;
            try {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(MainActivity.this, uri);
                art = mmr.getEmbeddedPicture();
                mmr.release();
            } catch (Exception ignored) {}
            final byte[] data = art;
            runOnUiThread(() -> {
                if (data != null && data.length > 0) {
                    try {
                        ivPoster.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
                        ivPoster.setVisibility(View.VISIBLE);
                        tvPoster.setVisibility(View.GONE);
                    } catch (Exception e) {
                        ivPoster.setVisibility(View.GONE);
                        tvPoster.setVisibility(View.VISIBLE);
                    }
                } else {
                    ivPoster.setVisibility(View.GONE);
                    tvPoster.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void loadSmi(String audioPath) {
        smi = new SmiParser();
        curLrcIdx = -1;
        boolean ok = false;
        if (audioPath != null) {
            int dot = audioPath.lastIndexOf('.');
            if (dot > 0) {
                String smiPath = audioPath.substring(0, dot) + ".smi";
                ok = smi.parseFile(smiPath);
                // 再试 .SMI 大写
                if (!ok) ok = smi.parseFile(audioPath.substring(0, dot) + ".SMI");
            }
        }
        if (ok) tvLrcStatus.setText("歌词 · " + smi.size() + " 行");
        else tvLrcStatus.setText("");
        lyricAdapter.notifyDataSetChanged();
    }

    private void toggle() {
        try {
            if (player.isPlaying()) player.pause(); else player.start();
            updatePlayBtn();
        } catch (Exception ignored) {}
    }

    private void next() {
        if (playlist.isEmpty()) return;
        playAt((index + 1) % playlist.size());
    }

    private void prev() {
        if (playlist.isEmpty()) return;
        // 上一首不重排，直接往前
        playAt((index - 1 + playlist.size()) % playlist.size(), true);
    }

    private void updatePlayBtn() {
        try {
            btnPlay.setImageResource(player.isPlaying()
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(lrcTick);
        try { player.release(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    /** 从播放列表移除（不删手机里的文件） */
    private void removeAt(int pos) {
        if (pos < 0 || pos >= playlist.size()) return;
        boolean wasPlaying = (pos == index);
        playlist.remove(pos);
        if (playlist.isEmpty()) {
            try { player.reset(); } catch (Exception ignored) {}
            index = -1;
            tvTitle.setText("播放列表空了");
            tvArtist.setText("");
            ivPoster.setVisibility(View.GONE);
            tvPoster.setVisibility(View.VISIBLE);
            updatePlayBtn();
        } else if (wasPlaying) {
            // 删的是正在播的，播下一首
            if (index >= playlist.size()) index = 0;
            playAt(index);
        } else if (pos < index) {
            index--;
        }
        playlistAdapter.notifyDataSetChanged();
    }

    private class PlaylistAdapter extends BaseAdapter {
        @Override public int getCount() { return playlist.size(); }
        @Override public Object getItem(int p) { return playlist.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override
        public View getView(int position, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = getLayoutInflater().inflate(R.layout.item_playlist, parent, false);
            }
            Song s = playlist.get(position);
            TextView tvT = cv.findViewById(R.id.tv_item_title);
            TextView tvA = cv.findViewById(R.id.tv_item_artist);
            TextView tvPlaying = cv.findViewById(R.id.tv_item_playing);
            ImageButton btnDel = cv.findViewById(R.id.btn_item_delete);
            tvT.setText(s.title);
            tvA.setText(s.artist);
            tvPlaying.setVisibility(position == index ? View.VISIBLE : View.GONE);
            final int pos = position;
            final Song song = s;
            btnDel.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("从播放列表删除")
                        .setMessage("确定移除《" + song.title + "》吗？\n（手机里的文件不会被删除）")
                        .setPositiveButton("删除", (d, w) -> removeAt(pos))
                        .setNegativeButton("取消", null)
                        .show();
            });
            return cv;
        }
    }

    private class LyricAdapter extends BaseAdapter {
        @Override public int getCount() { return smi.size(); }
        @Override public Object getItem(int p) { return smi.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override
        public View getView(int position, View cv, ViewGroup parent) {
            TextView tv;
            if (cv == null) {
                tv = new TextView(MainActivity.this);
                tv.setPadding(16, 14, 16, 14);
                tv.setGravity(android.view.Gravity.CENTER);
            } else tv = (TextView) cv;
            tv.setText(smi.get(position).text);
            if (position == curLrcIdx) { tv.setTextColor(0xFF7DD3A8); tv.setTextSize(17); }
            else { tv.setTextColor(0xFF888888); tv.setTextSize(15); }
            return tv;
        }
    }
}
