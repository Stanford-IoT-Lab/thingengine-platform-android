package edu.stanford.thingengine.engine.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.percent.PercentRelativeLayout;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONException;

import java.util.ArrayList;

import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AssistantHistoryModel;
import edu.stanford.thingengine.engine.service.AssistantMessage;

/**
 * Created by gcampagn on 8/22/16.
 */
class AssistantHistoryAdapter extends RecyclerView.Adapter<AssistantHistoryAdapter.AssistantMessageViewHolder> implements AssistantHistoryModel.Listener {
    private AssistantFragment fragment;

    public abstract static class AssistantMessageViewHolder extends RecyclerView.ViewHolder {
        protected final Context ctx;
        private AssistantMessage.Direction cachedSide = null;

        public AssistantMessageViewHolder(Context ctx) {
            super(new PercentRelativeLayout(ctx));
            itemView.setPadding(0, 0, 0, 0);
            this.ctx = ctx;
        }

        private PercentRelativeLayout getWrapper() {
            return (PercentRelativeLayout) itemView;
        }

        public abstract void bind(AssistantMessage msg);

        protected void applyBubbleStyle(View view, AssistantMessage.Direction side) {
            if (side == AssistantMessage.Direction.FROM_SABRINA)
                view.setBackgroundResource(R.drawable.bubble_sabrina);
            else if (side == AssistantMessage.Direction.FROM_USER)
                view.setBackgroundResource(R.drawable.bubble_user);
        }

        protected void setSideAndAlignment(View view, AssistantMessage.Direction side) {
            if (side == cachedSide)
                return;

            if (cachedSide != null)
                getWrapper().removeView(view);
            cachedSide = side;

            PercentRelativeLayout.LayoutParams params = new PercentRelativeLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.getPercentLayoutInfo().widthPercent = 0.7f;
            params.setMargins(0, 0, 0, 0);

            if (view instanceof android.widget.Button) {
                ((android.widget.Button) view).setTransformationMethod(null);
                // indent the buttons
                params.getPercentLayoutInfo().startMarginPercent = 0.05f;
                params.getPercentLayoutInfo().widthPercent = 0.6f;
            }

            if (side == AssistantMessage.Direction.FROM_SABRINA) {
                params.addRule(RelativeLayout.ALIGN_PARENT_START);
                if (view instanceof TextView && !(view instanceof android.widget.Button)) {
                    ((TextView) view).setGravity(Gravity.START);
                    ((TextView) view).setTextIsSelectable(true);
                }
                getWrapper().addView(view, params);
                view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            } else if (side == AssistantMessage.Direction.FROM_USER) {
                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                if (view instanceof TextView && !(view instanceof android.widget.Button)) {
                    ((TextView) view).setGravity(Gravity.END);
                    ((TextView) view).setTextIsSelectable(true);
                }
                getWrapper().addView(view, params);
                view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            }
        }

        public static class Text extends AssistantMessageViewHolder {
            private TextView view;

            public Text(Context ctx) {
                super(ctx);
            }

            @Override
            public void bind(AssistantMessage msg) {
                if (view == null)
                    view = new TextView(ctx);
                view.setText(((AssistantMessage.Text) msg).msg);
                applyBubbleStyle(view, msg.direction);
                setSideAndAlignment(view, msg.direction);
            }
        }

        public static class Picture extends AssistantMessageViewHolder {
            private ImageView view;
            private String cachedUrl;
            protected final AssistantFragment owner;

            public Picture(Context ctx, AssistantFragment owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(final AssistantMessage base) {
                if (view == null) {
                    view = new ImageView(ctx);
                    view.setBackgroundColor(Color.RED);
                    view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    view.setAdjustViewBounds(true);
                }
                final AssistantMessage.Picture msg = (AssistantMessage.Picture)base;
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.showPictureFullscreen(msg.url);
                    }
                });
                if (cachedUrl != null && cachedUrl.equals(msg.url))
                    return;
                cachedUrl = msg.url;
                new LoadImageTask(ctx, view).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, msg.url);
                applyBubbleStyle(view, msg.direction);
                setSideAndAlignment(view, msg.direction);
            }
        }

        public static abstract class AbstractButton extends AssistantMessageViewHolder {
            protected android.widget.Button btn;
            protected final AssistantFragment owner;

            public AbstractButton(Context ctx, AssistantFragment owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(AssistantMessage msg) {
                if (btn == null)
                    btn = new android.widget.Button(ctx);
                setSideAndAlignment(btn, msg.direction);
            }
        }

        public static class RDL extends AbstractButton {
            public RDL(Context ctx, AssistantFragment owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                final AssistantMessage.RDL msg = (AssistantMessage.RDL)base;
                try {
                    btn.setText(msg.rdl.optString("displayTitle"));
                    String webCallback = msg.rdl.getString("webCallback");
                    final String url;
                    if (webCallback.startsWith("http"))
                        url = webCallback;
                    else
                        url = "http://" + webCallback;
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            owner.onLinkActivated(url);
                        }
                    });
                } catch (JSONException e) {
                    Log.e(MainActivity.LOG_TAG, "Unexpected JSON exception while unpacking RDL", e);
                }
            }
        }

        public static class Button extends AbstractButton {
            public Button(Context ctx, AssistantFragment owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                final AssistantMessage.Button msg = (AssistantMessage.Button)base;
                btn.setText(msg.title);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.onButtonActivated(msg.json);
                    }
                });
            }
        }

        public static class Link extends AbstractButton {
            public Link(Context ctx, AssistantFragment owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                final AssistantMessage.Link msg = (AssistantMessage.Link)base;
                btn.setText(msg.title);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.onLinkActivated(msg.url);
                    }
                });
            }
        }

        public static class Choice extends AbstractButton {
            public Choice(Context ctx, AssistantFragment owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                final AssistantMessage.Choice msg = (AssistantMessage.Choice)base;
                btn.setText(msg.title);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.onChoiceActivated(msg.idx);
                    }
                });
            }
        }

        public static class YesNo extends AssistantMessageViewHolder {
            private android.widget.Button yesbtn;
            private android.widget.Button nobtn;
            private LinearLayout yesno;
            private final AssistantFragment owner;

            public YesNo(Context ctx, AssistantFragment owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(AssistantMessage msg) {
                if (yesno == null) {
                    yesno = new LinearLayout(ctx);
                    yesno.setOrientation(LinearLayout.HORIZONTAL);
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (yesbtn == null) {
                    yesbtn = new android.widget.Button(ctx);
                    yesbtn.setText(R.string.yes);
                    yesbtn.setLayoutParams(params);
                    yesbtn.setText(R.string.yes);
                    yesbtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            owner.onYesActivated();
                        }
                    });
                    yesno.addView(yesbtn);
                }
                if (nobtn == null) {
                    nobtn = new android.widget.Button(ctx);
                    nobtn.setLayoutParams(params);
                    nobtn.setText(R.string.no);
                    nobtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            owner.onNoActivated();
                        }
                    });
                    yesno.addView(nobtn);
                }

                setSideAndAlignment(yesno, msg.direction);
            }
        }

        public static class ChooseLocation extends AbstractButton {
            public ChooseLocation(Context ctx, AssistantFragment owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                btn.setText(R.string.btn_choose_location);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.showLocationPicker();
                    }
                });
            }
        }

        public static class ChoosePicture extends AbstractButton {
            public ChoosePicture(Context ctx, AssistantFragment owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                btn.setText(R.string.btn_choose_picture);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.showImagePicker();
                    }
                });
            }
        }

        public static class ChooseContact extends AbstractButton {
            public ChooseContact(Context ctx, AssistantFragment owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                btn.setText(R.string.btn_choose_contact);

                final AssistantMessage.AskSpecial msg = (AssistantMessage.AskSpecial)base;
                final int requestCode = msg.what == AssistantMessage.AskSpecialType.PHONE_NUMBER ? AssistantFragment.REQUEST_PHONE_NUMBER : AssistantFragment.REQUEST_EMAIL;
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.showContactPicker(requestCode);
                    }
                });
            }
        }
    }

    private final ArrayList<AssistantMessage> filteredHistory = new ArrayList<>();
    private Context ctx;
    private AssistantHistoryModel history;

    public AssistantHistoryAdapter(AssistantFragment fragment) {
        this.fragment = fragment;
    }

    @Override
    public int getItemCount() {
        return filteredHistory.size();
    }

    public AssistantMessage getItem(int position) {
        return filteredHistory.get(position);
    }

    @Override
    public long getItemId(int position) {
        // AssistantMessages are immutable so we can hash them to find the item id
        return getItem(position).hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        AssistantMessage msg = getItem(position);
        if (msg.type == AssistantMessage.Type.ASK_SPECIAL)
            return 100 + ((AssistantMessage.AskSpecial)msg).what.ordinal();
        else
            return msg.type.ordinal();
    }

    @Override
    public void onBindViewHolder(AssistantMessageViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public AssistantMessageViewHolder onCreateViewHolder(ViewGroup group, int viewType) {
        AssistantMessage.Type type;
        AssistantMessage.AskSpecialType askSpecialType;
        if (viewType >= 100) {
            type = AssistantMessage.Type.ASK_SPECIAL;
            askSpecialType = AssistantMessage.AskSpecialType.values()[viewType-100];
        } else {
            type = AssistantMessage.Type.values()[viewType];
            askSpecialType = null;
        }

        switch (type) {
            case TEXT:
                return new AssistantMessageViewHolder.Text(getContext());
            case PICTURE:
                return new AssistantMessageViewHolder.Picture(getContext(), fragment);
            case RDL:
                return new AssistantMessageViewHolder.RDL(getContext(), fragment);
            case CHOICE:
                return new AssistantMessageViewHolder.Choice(getContext(), fragment);
            case LINK:
                return new AssistantMessageViewHolder.Link(getContext(), fragment);
            case BUTTON:
                return new AssistantMessageViewHolder.Button(getContext(), fragment);
            case ASK_SPECIAL:
                assert askSpecialType != null;
                switch (askSpecialType) {
                    case YESNO:
                        return new AssistantMessageViewHolder.YesNo(getContext(), fragment);

                    case LOCATION:
                        return new AssistantMessageViewHolder.ChooseLocation(getContext(), fragment);

                    case PICTURE:
                        return new AssistantMessageViewHolder.ChoosePicture(getContext(), fragment);

                    case PHONE_NUMBER:
                    case EMAIL_ADDRESS:
                        return new AssistantMessageViewHolder.ChooseContact(getContext(), fragment);

                    case UNKNOWN:
                    case ANYTHING:
                        // we don't recognize this, it should have been filtered by isFiltered()
                        throw new RuntimeException();

                    default:
                        throw new RuntimeException();
                }
            default:
                throw new RuntimeException();
        }
    }

    private boolean isFiltered(AssistantMessage msg) {
        if (msg.type == AssistantMessage.Type.ASK_SPECIAL) {
            AssistantMessage.AskSpecial askSpecial = (AssistantMessage.AskSpecial) msg;
            if (askSpecial.what == AssistantMessage.AskSpecialType.UNKNOWN)
                return true;
        }

        return false;
    }

    @Override
    public void onAdded(AssistantMessage msg) {
        if (isFiltered(msg))
            return;

        filteredHistory.add(msg);
        notifyItemInserted(filteredHistory.size() - 1);
    }

    @Override
    public void onClear() {
        filteredHistory.clear();
        notifyDataSetChanged();
    }

    public void setContext(Context ctx) {
        this.ctx = ctx;
    }

    private Context getContext() {
        return ctx;
    }

    public void setHistory(AssistantHistoryModel history) {
        if (history == this.history)
            return;
        if (this.history != null)
            this.history.removeListener(this);
        this.history = history;
        if (history == null)
            return;
        history.addListener(this);

        filteredHistory.clear();

        for (AssistantMessage msg : history) {
            if (isFiltered(msg))
                continue;
            filteredHistory.add(msg);
        }
        notifyDataSetChanged();
    }
}