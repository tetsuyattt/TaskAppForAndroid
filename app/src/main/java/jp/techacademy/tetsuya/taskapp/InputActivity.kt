package jp.techacademy.tetsuya.taskapp

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import jp.techacademy.tetsuya.taskapp.databinding.ActivityInputBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

//ほぼ全て追加７（３）
class InputActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInputBinding

    private lateinit var realm: Realm
    private lateinit var task: Task
    private var calender = Calendar.getInstance()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //アクションバーの設定　setSupportActionBar()によりツールバーをActionBarとして利用できる
        setSupportActionBar(binding.toolber)
        if (supportActionBar != null) {

            //ActionBarに戻るボタンを表示
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }

        //ボタンのイベントリスナー設定
        // res/layout/activity_input.xmlのincludeでcontentを指定するのだいじ
        binding.content.dateButton.setOnClickListener(dateClickListener)
        binding.content.timeButton.setOnClickListener(timeClickListener)
        binding.content.doneButton.setOnClickListener(doneClickListener)

        //EXTRA_TASKからTaskのidを取得
        //遷移元からのひっぱてきたデータ　EXTRA_TASKが設定されていない場合、taskIdには「−１」が代入される
        val intent = intent
        val taskId = intent.getIntExtra(EXTRA_TASK, -1)


        //Realmデータベースとの接続を開く
        //config=構成
        val config = RealmConfiguration.create(schema = setOf(Task::class))
        realm = Realm.open(config)

        //タスクを取得または初期化
        initTask(taskId)
    }

    override fun onDestroy() {
        super.onDestroy()
        //Realmデータベースとの接続を閉じる
        realm.close()
    }

    //日付選択ボタンをクリックした時の処理
    private val dateClickListener = View.OnClickListener {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                //calender.set(year, month, day)で入力された日付で更新する
                calender.set(year, month, day)

                //setDateTimeButtonText()で、ボタンに日付を表示させる
                setDateTimeButtonText()//一番下にある
            },
            calender.get(Calendar.YEAR),
            calender.get(Calendar.MONTH),
            calender.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    //時刻選択ボタン
    private val timeClickListener = View.OnClickListener {
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hour, minute ->
                calender.set(Calendar.HOUR_OF_DAY, hour)
                calender.set(Calendar.MINUTE, minute)
                setDateTimeButtonText()
            }, calender.get(Calendar.HOUR_OF_DAY), calender.get(Calendar.MINUTE), true
        )
        timePickerDialog.show()
    }

    //決定ボタン
    private val doneClickListener = View.OnClickListener {
        // addTask()のrealm.writeが非同期のためコルーチン
        CoroutineScope(Dispatchers.Default).launch {
            //保存・更新して
            addTask()
            //finish()メソッドでInputActivityを閉じて前の画面（MainActivity）に戻る
            finish()
        }
    }


    //タスクの取得または初期化メソッド　initTask()
    //画面遷移時に引数として渡されてきたtaskIdに合致するタスクを検索して表示する
    /*MainActivityのFloatingActionButtonをタップしてInputActivityを起動した場合、taskIdには-1がセットされ、
    findTaskはnullになる
    */
    private fun initTask(taskId: Int) {

        //引数のtaskIdに合致するタスクを検索するんだけど、
        val findTask = realm.query<Task>("id==$taskId").first().find()

        //nullの時、新規作成
        if (findTask == null) {
            task = Task()
            task.id = -1
            //日付の初期値を１日後に設定
            calender.add(Calendar.DAY_OF_MONTH, 1)

            //それ以外は既存のデータあるので更新
        } else {
            task = findTask
            //taskの日時をcalenderに反映
            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPANESE)
            //parse=文法的関係を解析する
            calender.time = simpleDateFormat.parse(task.date) as Date

            //taskの値を画面項目に反映
            binding.content.titleEditText.setText(task.title)
            binding.content.contentEditText.setText(task.contents)
            binding.content.categoryEditText.setText(task.category) //課題　追加２　まず保存
        }
        //日付と日時ボタンの表示を設定
        setDateTimeButtonText()
    }

    //タスクの登録または更新をする
    private suspend fun addTask() {
        //日付型オブジェクトを文字列に変換するフォーマットを作成
        //Date型ではなく、文字列で保存するため
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPANESE)

        //登録（更新）する値を取得
        val title = binding.content.titleEditText.text.toString()
        val content = binding.content.contentEditText.text.toString()
        val category = binding.content.categoryEditText.text.toString()//課題　追加２
        val date = simpleDateFormat.format(calender.time)

        //新規登録の時
        /*最初、新規登録してもListViewに新しいのが登録されなかったが、
        原因は「task.id == -1」の部分が「「taskId == -1」」になってたからだった。
        これだとtask.idはnullだから、条件に合わずRealmに登録されてなかったっぽい
        */
        if (task.id == -1) {
            //最大のid+1をセットして、
            task.id = (realm.query<Task>().max("id", Int::class).find() ?: -1) + 1
            //画面項目の値で更新
            task.title = title
            task.contents = content
            task.category = category //課題　追加２
            task.date = date

            //登録処理する
            realm.writeBlocking {
                //データベースへの保存処理
                copyToRealm(task)
            }

        //既存データを更新する時
            } else {
                realm.write {
                    findLatest(task)?.apply {
                        //画面項目の値で更新
                        this.title = title
                        this.contents = content
                        this.category = category //課題　追加２
                        this.date = date

                    }
                }
            }


        //ーーーーーーーここから追加８（２）　アラームの実装 TaskAlarmRecieverクラスとパーミッションを実装してからここにくる
        //タスク登録時に、アラームにも登録する的な感じ
        //タスク日時にアラームを設定
        //TaskAlarmRecieverを起動するintentを作成　どのタスク情報を通知するかを渡してる
        val intent = Intent(applicationContext, TaskAlarmReciever::class.java)
        intent.putExtra(EXTRA_TASK, task.id)
        //スケジュールされた日時でブロードキャストするためのpendingIntentを作成
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            task.id,
            intent,

            //immutable = 不変の
            /*既存のpendingIntentがある場合(タスクを更新した時)、PendingIntent.FLAG_UPDATE_CURRENTにて、
            それはそのままでextraのデータ（タスクのデータ）だけ置き換える。
            Android12以降は「PendingIntentの可変性」を指定する　→　PendingIntent.FLAG_IMMUTABLE
            →何のこと言ってるかわかんないけどわかんないままでいいかな
             */
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        )

        //AlarmManagerで、指定した時間に任意の処理を行わせることができる
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        //AlarmClockInfoでアラームの起動時間をTUC時間で指定
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(calender.timeInMillis, null), pendingIntent)

    //ーーーーーーーここまで追加８（２）　アラームの実装　→アラームの削除へ（MainActivity.ktのonCreate()へ）
        }

    //日付と時刻のボタンの表示を設定するメソッド
    private fun setDateTimeButtonText() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPANESE)
        binding.content.dateButton.text = dateFormat.format(calender.time)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.JAPANESE)
        binding.content.timeButton.text = timeFormat.format(calender.time)

    }


}