package mbv.hmtproject;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import mbv.hmtproject.datatypes.Scoreboard;
import mbv.hmtproject.datatypes.StopRoute;


public class ScoreboardAdapter extends BaseAdapter {
    public Scoreboard scoreboard;
    Activity activity;

    public ScoreboardAdapter(Activity activity, Scoreboard scoreboard) {
        super();
        this.scoreboard = scoreboard;
        this.activity = activity;
    }

    @Override
    public int getCount() {
        return scoreboard.Routes.size();
    }

    @Override
    public Object getItem(int position) {
        return scoreboard.Routes.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private class ViewHolder {
        TextView mNumber;
        TextView mEndStop;
        TextView mNearest;
        TextView mNext;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        LayoutInflater inflater = activity.getLayoutInflater();

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.scoreboard_table_row, null);
            holder = new ViewHolder();
            holder.mNumber = (TextView) convertView.findViewById(R.id.sbt_row_number);
            holder.mEndStop = (TextView) convertView.findViewById(R.id.sbt_row_end_stop);
            holder.mNearest = (TextView) convertView
                    .findViewById(R.id.sbt_row_nearest);
            holder.mNext = (TextView) convertView.findViewById(R.id.sbt_row_next);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        StopRoute item = scoreboard.Routes.get(position);
        holder.mNumber.setText(item.getFullNumber());
        holder.mEndStop.setText(item.EndStop);
        holder.mNearest.setText(item.Nearest);
        holder.mNext.setText(item.Next);

        return convertView;
    }

}
