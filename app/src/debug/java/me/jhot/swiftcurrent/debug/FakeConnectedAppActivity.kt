package me.jhot.swiftcurrent.debug

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Shows which data row it was opened for, so a tap on a connected-app button is visibly a launch of that row. */
class FakeConnectedAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Fake connected app\n${intent.type}\n${intent.data}"; textSize = 20f; setPadding(48, 200, 48, 48) })
    }
}
