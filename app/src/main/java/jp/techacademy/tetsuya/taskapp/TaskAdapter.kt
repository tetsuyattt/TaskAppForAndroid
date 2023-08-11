package jp.techacademy.tetsuya.taskapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.text.FieldPosition

//追加２　BaseAdapterクラスに必須メソッド「getCount（）」「getItem（）」「getItemId（）」「getView（）」
//追加５（２）　ArrayListの型をTaskクラスへ
class TaskAdapter(context: Context): BaseAdapter() {
    private val layoutInflater: LayoutInflater
    //mutable=可変
    private var taskList = mutableListOf<Task>()

    init {
        //context = 文脈
        this.layoutInflater = LayoutInflater.from(context)
    }

    //データの個数を返す
    override fun getCount(): Int {
        return taskList.size
    }

    //データの内容を返す
    override fun getItem(position: Int): Any {
        return taskList[position]
    }

    //データのIDを返す
    override fun getItemId(position: Int): Long {
        return taskList[position].id.toLong()//追加５（２）
    }

    //Viewを返す
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View =
            convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)

        val textView1 = view.findViewById<TextView>(android.R.id.text1)
        val textView2 = view.findViewById<TextView>(android.R.id.text2)

        //あとで、Taskクラスから情報を取得するように変更する予定 →追加５（２）にて変更済み
        textView1.text = taskList[position].title
        textView2.text = taskList[position].date

        return view
    }

    fun updateTaskList(taskList: List<Task>) {
        //一度クリアしてから新しいタスク一覧に入替える
        this.taskList.clear()
        this.taskList.addAll(taskList)

        //↓データに変更があったことをadapterに通知する
        notifyDataSetChanged()

    }

}