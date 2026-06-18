package git.artdeell.skymodloader.elfmod;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Optional;

import git.artdeell.skymodloader.R;
import git.artdeell.skymodloader.databinding.ModListElementBinding;

public class ModListAdapter extends RecyclerView.Adapter<ModListAdapter.ViewHolder> {
    final ElfUIBackbone loader;

    public ModListAdapter(ElfUIBackbone loader) {
        this.loader = loader;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ModListElementBinding binding = DataBindingUtil.inflate(LayoutInflater.from(loader.activity), R.layout.mod_list_element, parent, false);
        ViewHolder vh = new ViewHolder(binding.getRoot());
        vh.binding = binding;
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bindMod(position);
    }

    @Override
    public int getItemCount() {
        return loader.getModsCount();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        int which;
        View myView;
        public ModListElementBinding binding;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            myView = itemView;
        }

        void bindMod(int which) {
            this.which = which;
            ElfModUIMetadata metadata = loader.getMod(which);
            metadata.which = which;
            metadata.loader = loader;
            if (metadata.bitmapIcon != null && metadata.bitmapIcon.getWidth() != 0 && metadata.bitmapIcon.getHeight() != 0) {
                ((ImageView)myView.findViewById(R.id.image_icon)).setImageBitmap(metadata.bitmapIcon);
            }

            LinearLayout checkForUpdatesLayout = myView.findViewById(R.id.check_for_updates);
            TextView textUpdate = myView.findViewById(R.id.text_update);
            ImageView imageUpdate = myView.findViewById(R.id.image_update);
            String githubReleasesRegex = "https://api\\.github\\.com/repos/.+/releases/latest";
            boolean hasGithubUpdates = metadata.githubReleasesUrl != null && metadata.githubReleasesUrl.matches(githubReleasesRegex);
            boolean hasOffsetsUpdates = metadata.hasOffsetsUrl();

            if (hasGithubUpdates || hasOffsetsUpdates) {
                checkForUpdatesLayout.setEnabled(true);
                checkForUpdatesLayout.setVisibility(View.VISIBLE);
                textUpdate.setText(R.string.check_for_updates);
                textUpdate.setTextColor(ContextCompat.getColor(myView.getContext(), R.color.text));
                imageUpdate.clearColorFilter();
            } else {
                checkForUpdatesLayout.setEnabled(false);
                textUpdate.setText(R.string.no_update_links);
                textUpdate.setTextColor(ContextCompat.getColor(myView.getContext(), android.R.color.darker_gray));
                imageUpdate.setColorFilter(ContextCompat.getColor(myView.getContext(), android.R.color.darker_gray));
            }

            myView.findViewById(R.id.check_for_updates).setOnClickListener(v -> {
                Log.i("AA", "onClick()");
                onCheckForUpdates(metadata);
            });
            myView.findViewById(R.id.import_offsets).setOnClickListener(v -> {
                if (loader.activity instanceof ModManagerActivity) {
                    ((ModManagerActivity) loader.activity).showImportOffsetsDialog(metadata);
                }
            });
            binding.setItem(metadata);
        }


        @Override
        public void onClick(View v) {
        }

        private void onCheckForUpdates(ElfModUIMetadata metadata) {
            loader.modUpdater.startModUpdater(metadata);
        }
    }

    public static String getVisibleModName(ElfModUIMetadata metadata) {
        return Optional.ofNullable(metadata.displayName).orElse(metadata.name);
    }
}
