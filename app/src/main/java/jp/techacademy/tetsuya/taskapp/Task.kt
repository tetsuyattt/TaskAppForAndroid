package jp.techacademy.tetsuya.taskapp

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.io.Serializable

//Serializable = 直列化　オブジェクトを出力ストリームに書き出すこと　→？？
//１つのidに属するデータ（紐づけられたデータ）をそのまま丸ごとファイルに保存できるようになる
open class Task : RealmObject, Serializable {
    //idをプライマリーキーとして設定
    @PrimaryKey
    var id = 0

    var title = ""  //タイトル
    var contents = ""//内容
    var date = ""   //日時
    var category = ""//課題　追加１　String型カテゴリー　→layout　content_input,valuesのstringへ
}