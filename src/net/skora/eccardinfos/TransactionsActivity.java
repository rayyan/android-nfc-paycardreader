package net.skora.eccardinfos;

import java.util.Arrays;

import android.app.ListActivity;
import android.content.Intent;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;

public class TransactionsActivity extends ListActivity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.transactions);
	    
	    Intent intent = this.getIntent();
		MatrixCursor cursor = new MatrixCursor(new String[] {"_id", "action", "amount", "hknr", "date", "time"});
		for (Integer i = 1; i <= 15; i++) {
			byte[] recv = intent.getByteArrayExtra(String.format("blog_%d", i));
			if (recv == null) continue;
			Integer len = recv.length;
			if (len > 33 && recv[len - 2] == (byte) 0x90 && recv[len - 1] == 0) {
				String action = SharedUtils.parseLogState(recv[0]);
				String amount = SharedUtils.formatBCDAmount(Arrays.copyOfRange(recv, 17, 20));
				String hknr = SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 3, 13), "");
				if (hknr.equals("00000000000000000000")) continue;
				String date = String.format("%02x.%02x.%02x%02x", recv[31], recv[30], recv[28], recv[29]);
				String time = String.format("%02x:%02x", recv[32], recv[33]);
				cursor.addRow(new String[] {i.toString(), action, amount, hknr, date, time});
			}
		}
		
		ListAdapter adapter = new SimpleCursorAdapter(
				this,
				R.layout.transaction_listitem,
				cursor,
				new String[] {"action", "amount", "hknr", "date", "time"},
				new int[] {R.id.listitem_action, R.id.listitem_amount, R.id.listitem_hknr, R.id.listitem_date, R.id.listitem_time}
		);
		setListAdapter(adapter);
	}
	
}
