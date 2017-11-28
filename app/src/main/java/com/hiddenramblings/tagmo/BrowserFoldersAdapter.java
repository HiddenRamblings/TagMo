package com.hiddenramblings.tagmo;

import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

class BrowserFoldersAdapter extends RecyclerView.Adapter<BrowserFoldersAdapter.FolderViewHolder> implements BrowserSettings.BrowserSettingsListener {
    public static final int PARENT_FOLDER_VIEW_TYPE = 0;
    public static final int CHILD_FOLDER_VIEW_TYPE = 1;

    BrowserSettings settings;
    ArrayList<File> data;
    File rootFolder;
    boolean showUpFolder = false;
    boolean firstRun = true;

    public BrowserFoldersAdapter(BrowserSettings settings) {
        this.settings = settings;
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings, BrowserSettings oldBrowserSettings) {
        if (firstRun || !Util.equals(newBrowserSettings.getBrowserRootFolder(), oldBrowserSettings.getBrowserRootFolder())) {
            this.rootFolder = newBrowserSettings.getBrowserRootFolder();
            this.showUpFolder = showParentFolder();
            this.notifyDataSetChanged();
        }
        if (firstRun || !Util.equals(newBrowserSettings.getFolders(), oldBrowserSettings.getFolders())) {
            this.data = newBrowserSettings.getFolders();
            this.notifyDataSetChanged();
        }
        firstRun = false;
    }

    @Override
    public FolderViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
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
    public void onBindViewHolder(FolderViewHolder holder, int position) {
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
        return (rootFolder != null && !rootFolder.equals(Util.getSDCardDir())) && rootFolder.getAbsolutePath().startsWith(Util.getSDCardDir().getAbsolutePath());
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
                .inflate(R.layout.parent_folder_view, parent, false));
        }

        public ParentFolderViewHolder(View itemView) {
            super(itemView);

            this.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    settings.setBrowserRootFolder(folder);
                    settings.notifyChanges();
                }
            });
        }

        @Override
        void bind(BrowserSettings settings, File folder) {
            this.settings = settings;
            this.folder = folder;
        }
    }

    static class ChildFolderViewHolder extends FolderViewHolder {
        BrowserSettings settings;
        File folder;
        TextView txtFolderName;

        public ChildFolderViewHolder(ViewGroup parent) {
            this(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.child_folder_view, parent, false));
        }

        public ChildFolderViewHolder(View itemView) {
            super(itemView);

            this.txtFolderName = itemView.findViewById(R.id.text);
            this.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    settings.setBrowserRootFolder(folder);
                    settings.notifyChanges();
                }
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
