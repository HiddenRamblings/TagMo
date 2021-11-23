package com.hiddenramblings.tagmo.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.settings.BrowserSettings;

import java.io.File;
import java.util.ArrayList;

public class BrowserFoldersAdapter
        extends RecyclerView.Adapter<BrowserFoldersAdapter.FolderViewHolder>
        implements BrowserSettings.BrowserSettingsListener {
    static final int PARENT_FOLDER_VIEW_TYPE = 0;
    static final int CHILD_FOLDER_VIEW_TYPE = 1;

    BrowserSettings settings;
    ArrayList<File> data;
    File rootFolder;
    boolean showUpFolder = false;
    boolean firstRun = true;

    public BrowserFoldersAdapter(BrowserSettings settings) {
        this.settings = settings;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings, BrowserSettings oldBrowserSettings) {
        if (newBrowserSettings == null || oldBrowserSettings == null) return;
        if (firstRun || !BrowserSettings.equals(newBrowserSettings.getBrowserRootFolder(), oldBrowserSettings.getBrowserRootFolder())) {
            this.rootFolder = newBrowserSettings.getBrowserRootFolder();
            this.showUpFolder = showParentFolder();
            this.notifyDataSetChanged();
        }
        if (firstRun || !BrowserSettings.equals(newBrowserSettings.getFolders(), oldBrowserSettings.getFolders())) {
            this.data = newBrowserSettings.getFolders();
            this.notifyDataSetChanged();
        }
        firstRun = false;
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case PARENT_FOLDER_VIEW_TYPE:
                return new ParentFolderViewHolder(parent);
            case CHILD_FOLDER_VIEW_TYPE:
                return new ChildFolderViewHolder(parent);
            default:
                throw new RuntimeException();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        File folder;
        if (holder instanceof ParentFolderViewHolder) {
            folder = this.rootFolder.getParentFile();
        } else {
            if (this.showUpFolder) {
                position -= 1;
            }
            folder = this.data.get(position);
        }
        holder.bind(this.settings, folder);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && this.showUpFolder) {
            return PARENT_FOLDER_VIEW_TYPE;
        } else {
            return CHILD_FOLDER_VIEW_TYPE;
        }
    }

    public boolean showParentFolder() {
        boolean internal = TagMo.getPrefs().preferEmulated().get();
        return (rootFolder != null && !Storage.getFile(internal).equals(rootFolder))
                && rootFolder.getAbsolutePath().startsWith(Storage.getPath(internal));
    }

    @Override
    public int getItemCount() {
        int count;
        if (this.data == null) {
            count = 0;
        } else {
            count = this.data.size();
        }
        if (this.showUpFolder) {
            count += 1;
        }
        return count;
    }

    static abstract class FolderViewHolder extends RecyclerView.ViewHolder {
        public FolderViewHolder(View itemView) {
            super(itemView);
        }

        abstract void bind(BrowserSettings settings, File folder);
    }

    static class ParentFolderViewHolder extends FolderViewHolder {
        BrowserSettings settings;
        File folder;

        public ParentFolderViewHolder(ViewGroup parent) {
            this(LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.folder_view_parent, parent, false));
        }

        public ParentFolderViewHolder(View itemView) {
            super(itemView);

            this.itemView.setOnClickListener(view -> {
                settings.setBrowserRootFolder(folder);
                settings.notifyChanges();
            });
        }

        @Override
        void bind(BrowserSettings settings, File folder) {
            this.settings = settings;
            this.folder = folder;
        }
    }

    private static class ChildFolderViewHolder extends FolderViewHolder {
        BrowserSettings settings;
        File folder;
        TextView txtFolderName;

        public ChildFolderViewHolder(ViewGroup parent) {
            this(LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.folder_view_child, parent, false));
        }

        public ChildFolderViewHolder(View itemView) {
            super(itemView);

            this.txtFolderName = itemView.findViewById(R.id.text);
            this.itemView.setOnClickListener(view -> {
                settings.setBrowserRootFolder(folder);
                settings.notifyChanges();
            });
        }

        @Override
        void bind(BrowserSettings settings, File folder) {
            this.settings = settings;
            this.folder = folder;
            this.txtFolderName.setText(folder.getName());
        }
    }
}
