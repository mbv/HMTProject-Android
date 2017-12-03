package mbv.hmtproject

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

import mbv.hmtproject.datatypes.Scoreboard
import mbv.hmtproject.datatypes.StopRoute


class ScoreboardAdapter(internal var activity: Activity, var scoreboard: Scoreboard) : BaseAdapter() {

    override fun getCount(): Int {
        return scoreboard.Routes.size
    }

    override fun getItem(position: Int): Any {
        return scoreboard.Routes[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private inner class ViewHolder {
        internal var mNumber: TextView? = null
        internal var mEndStop: TextView? = null
        internal var mNearest: TextView? = null
        internal var mNext: TextView? = null
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val holder: ViewHolder
        val inflater = activity.layoutInflater

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.scoreboard_table_row, null)
            holder = ViewHolder()
            holder.mNumber = convertView!!.findViewById(R.id.sbt_row_number) as TextView
            holder.mEndStop = convertView.findViewById(R.id.sbt_row_end_stop) as TextView
            holder.mNearest = convertView
                    .findViewById(R.id.sbt_row_nearest) as TextView
            holder.mNext = convertView.findViewById(R.id.sbt_row_next) as TextView
            convertView.tag = holder
        } else {
            holder = convertView.tag as ViewHolder
        }

        val item = scoreboard.Routes[position]
        holder.mNumber!!.text = item.fullNumber
        holder.mEndStop!!.text = item.EndStop
        holder.mNearest!!.text = item.Nearest
        holder.mNext!!.text = item.Next

        return convertView
    }

}
