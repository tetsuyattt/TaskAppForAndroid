package jp.techacademy.tetsuya.taskapp

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.contains
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.delete
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.Sort
import jp.techacademy.tetsuya.taskapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList




//追加６ 画面遷移　IntentのExtraのキーワード定義
const val EXTRA_TASK = "jp.techacademy.tetsuya.taskapp.TASK"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    //追加５？　realm実装
    private lateinit var realm: Realm

    //課題　追加４　検索用searchView
    private lateinit var searchView: SearchView
//    private lateinit var searchList: ArrayList



    //追加８（４）ManifestのPOST_NOTIFICATIONについて、その許可を行う記述を追記
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted){
                //権限が許可された
                Log.d("ANDROID","許可された")
            } else {
                //拒否された
                Log.d("ANDROID","許可されなかった")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //ーーーーーここから追加８（４）ManifestのPOST_NOTIFICATIONについて、その許可を行う記述を追記
        //OSバージョン確認APIレベル３３以上の時
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            //通知権限が許可されているか確認
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                //権限許可済みなら　→何もしない？
                Log.d("ANDROID", "許可されている")
            } else {
                //許可されていない時は、許可ダイアログを表示
                Log.d("ANDROID", "許可されていない")
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            //APIレベルが３３以前のの時は、アプリごとの通知設定を確認する
        } else {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()){
                //OSバージョン確認　APIレベル２６以上の時は
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    //APIレベル２６以上なので直接通知の設定画面に遷移する
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                    }
                } else {
                    //APIレベル２６未満なら、アプリのシステム設定に遷移する
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    )
                }

                //生成されたインテントにIntent.FLAG_ACTIVITY_NEW_TASKを付加
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                //Activityを開始する
                startActivity(intent)
            }
            //ーーーーーーーーーーーここまで追加８（４
        }

        binding.fab.setOnClickListener {
            //追加６にて変更　画面遷移
//            Snackbar.make(v, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show()
            val intent = Intent(this, InputActivity::class.java)
            startActivity(intent)
        }

        //TaskAdapterを生成して、ListViewに設定する
        taskAdapter = TaskAdapter(this)
        binding.listView.adapter = taskAdapter

        //ListViewを「タップ」した時の処理
        //追加６にて変更　画面遷移　idをインテントで渡して遷移
        binding.listView.setOnItemClickListener { parent, view, position, id ->
            // TODO: 入力・編集画面に遷移させる　content get~かな？
            //入力・編集する画面に遷移させる
            val task = parent.adapter.getItem(position) as Task
            val intent = Intent(this, InputActivity::class.java)
            intent.putExtra(EXTRA_TASK, task.id)
            startActivity(intent)
        }

        //ListViewを 「長押し」した時の処理
        //追加６にて変更
        //長押しした時、ダイアログにて削除するか確認をして、OKしたら削除する
        binding.listView.setOnItemLongClickListener { parent, view, position, id ->
            //TODO: タスクを削除する　
            val task = parent.adapter.getItem(position) as Task
            //ダイアログを表示
            val builder = AlertDialog.Builder(this)
            builder.setTitle("削除")
            builder.setMessage(task.title + "削除しますか")

            builder.setPositiveButton("OK") {_, _ ->
                //Realmへの書き込みや削除処理 writeBlocking{}
                realm.writeBlocking {
                    //タスクIDに該当するデータを削除する
                    //選択したタスクと同じIDのものをqueryメソッドで検索して、deleteメソッドで削除
                    val tasks = query<Task>("id==${task.id}").find()
                    tasks.forEach {
                        delete(it)
                    }
                }

                //ーーーーーここから追加８（３）　アラーム削除
                //タスクを削除する時にアラーム登録も削除しとかないと、タスクがないのにアラーム出てくる
                //アラーム登録時と一緒？っぽい
                //TaskAlarmRecieverからきた時にどうするか、って処理
                val resultIntent = Intent(applicationContext, TaskAlarmReciever::class.java)
                resultIntent.putExtra(EXTRA_TASK, task.id)
                val resultPendingIntent = PendingIntent.getBroadcast(
                    this,
                    task.id,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(resultPendingIntent)

                //ここまで追加８（３）　→　通知設定は　TaskAlarmRecieverへ
            }

            //キャンセル押したら何もしない
            builder.setNegativeButton("CANCEL", null)


            val dialog = builder.create()
            dialog.show()

            true
        }
//        reloadListView() →　下記のCoroutineScope内のwhen以下へ


        //ーーーーーーーーーーーーここから追加５　realm実装
        //Realmデータベースからの接続を開く
        val config = RealmConfiguration.create(schema = setOf(Task::class))
        realm = Realm.open(config)


        //課題　追加５　検索用
        binding.searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener{
                //↓検索ボタンを押した時
                override fun onQueryTextSubmit(query: String?): Boolean {

//                        val filteredTasks = realm.where(Task::class)  //←ここでwhereが赤くなっています
//                            .contains("category", query)
//                            .find()
                        val filteredTasks = realm.query<Task>("category CONTAINS $0", query).find()
                        taskAdapter.updateTaskList(filteredTasks)

                    return false
                }

                //↓入力テキストに文字が入力されるたびに呼ばれる
                override fun onQueryTextChange(newText: String?): Boolean {
                    Log.e("MainActivity", "newText =: $newText")

//                        val filteredTasks = realm.where(Task::class)  //←ここでwhereが赤くなっています
//                            .contains("category", newText)
//                            .find()
//                        taskAdapter.updateTaskList(filteredTasks)

                        val filteredTasks = realm.query<Task>("category CONTAINS $0", newText).find()
                        taskAdapter.updateTaskList(filteredTasks)
                    return false
                }
            }
        )




        //Realmからタスク一覧を取得（日付でソート）
        val tasks = realm.query<Task>().sort("date", Sort.DESCENDING).find()

        //Realmが起動・更新（追加、変更、削除）した時に、reloadListViewを実行する
        CoroutineScope(Dispatchers.Default).launch {
            /*「tasks.asFlow().collect{}」でRealmの監視を行う　→kotlinでの非同期処理コルーチン　
            Androidでは、時間がかかる処理をメインスレッドではなく別スレッドで非同期で行う必要がある　→へー
            */
            tasks.asFlow().collect {
                when (it) {
                    //更新時
                    is UpdatedResults -> reloadListView(it.list)
                    //起動時
                    is InitialResults -> reloadListView(it.list)
                    else -> {}
                }
            }
        }
        //表示テスト用のタスクを生成してRealmに登録
//        addTaskForTest()　→追加６にて削除

    }


    //Activityが閉じられる時に呼ばれる
    override fun onDestroy() {
        super.onDestroy()
        //realmとの接続を閉じる
        realm.close()
    }

    //ーーーーーーーーーーーーここまで追加５　realm実装

    //リストの一覧を更新する
    /*reloadListViewは非同期で呼ばれるが、メインスレッドで処理を行う必要がある
    そのため、suspendキーワードで非同期であることを宣言して、withContext(Dispatchers.Main) {}でメインで処理する
     */
    private suspend fun reloadListView(tasks: List<Task>) {
//        //　TODO: あとでTaskクラスに変更する →　追加５で変更済
//        val tasks = mutableListOf("aaa", "bbb", "ccc")　→テスト用だったので削除
        withContext(Dispatchers.Main) {
            taskAdapter.updateTaskList(tasks)
        }
    }

    //追加５　realm実装　→　テスト用のため追加６にて削除
//    private fun addTaskForTest() {
//        //日付を文字列に変換するフォーマットを作成
//        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPANESE)
//        //Realmへの書き込みや削除処理 writeBlocking{}
//        realm.writeBlocking {
//            //登録済みデータがあれば削除
//            delete<Task>()
//
//            //idが０の新しいデータを１件登録　→暫定版だから後で
//            copyToRealm(Task().apply {
//                id = 0
//                title = "作業"
//                content = "プログラムを書いてPUSHする"
//                date = simpleDateFormat.format(Date())
//            })
//
//        }
//    }


}