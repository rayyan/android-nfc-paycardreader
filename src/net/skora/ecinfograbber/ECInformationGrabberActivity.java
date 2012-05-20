package net.skora.ecinfograbber;

import java.io.IOException;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.util.Log;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TableRow.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

public class ECInformationGrabberActivity extends Activity {
	// Dialogs
	private static final int DIALOG_NFC_NOT_AVAIL = 0;
	
	private static final String LOGTAG = "ECInfoGrabber";
	
	private NfcAdapter nfc;
	private Tag tag;
	private IsoDep tagcomm;
	private String[][] nfctechfilter = new String[][] { new String[] { NfcA.class.getName() } };
	private PendingIntent nfcintent;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
        	showDialog(DIALOG_NFC_NOT_AVAIL);
        }
        nfcintent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    }
    
	@Override
	protected void onResume() {
		super.onResume();
		nfc.enableForegroundDispatch(this, nfcintent, null, nfctechfilter);
	}

	@Override
	protected void onPause() {
		super.onPause();
		nfc.disableForegroundDispatch(this);
	}
	
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		log("Tag detected!");
		
		TextView nfcid = (TextView) findViewById(R.id.display_nfcid);
		nfcid.setText("-");
		TextView blz = (TextView) findViewById(R.id.display_blz);
		blz.setText("-");
		TextView konto = (TextView) findViewById(R.id.display_konto);
		konto.setText("-");
		TextView betrag = (TextView) findViewById(R.id.display_betrag);
		betrag.setText("-");
		TextView kartennr = (TextView) findViewById(R.id.display_kartennr);
		kartennr.setText("-");
		TextView aktiviert = (TextView) findViewById(R.id.display_aktiviert);
		aktiviert.setText("-");
		TextView verfall = (TextView) findViewById(R.id.display_verfall);
		verfall.setText("-");
		TableLayout aidtable = (TableLayout) findViewById(R.id.table_features);
		aidtable.removeAllViews();
		
		byte[] id = tag.getId();
		nfcid.setText(Byte2Hex(id));
		
		tagcomm = IsoDep.get(tag);
		if (tagcomm == null) {
			toastError(getResources().getText(R.string.error_nfc_comm));
		}
		try {
			tagcomm.connect();
		} catch (IOException e) {
			toastError(getResources().getText(R.string.error_nfc_comm_cont) + (e.getMessage() != null ? e.getMessage() : "-"));
		}
		
		try {
			// Switch to DF_BOERSE
			byte[] recv = transceive("00 A4 04 0C 09 D2 76 00 00 25 45 50 02 00");
			// Read EF_BETRAG
			recv = transceive("00 B2 01 C4 00");
			StringBuilder res = new StringBuilder(); 
			if (recv[0] != 0) res.append(Integer.toHexString(recv[0]));
			if (recv[1] == 0) {
				if (res.length() > 0) {
					res.append("00");
				} else {
					res.append("0");
				}
			} else {
				if (res.length() > 0 && recv[1] <= 9) {
					res.append("0");
				}
				res.append(Integer.toHexString(recv[1]));
			}
			res.append(",");
			String cents = Integer.toHexString(recv[2]);
			if (cents.length() == 1) res.append("0");
			res.append(cents);
			res.append("€");
			betrag.setText(res.toString());
			
			// Read EF_ID
			recv = transceive("00 B2 01 BC 00");
			// Kartennr.
			kartennr.setText(Byte2Hex(Arrays.copyOfRange(recv, 4, 9)).replace(" ", ""));
			//Aktiviert am
			aktiviert.setText(Byte2Hex(Arrays.copyOfRange(recv, 14, 15)).replace(" ", "") + "." + Byte2Hex(Arrays.copyOfRange(recv, 13, 14)).replace(" ", "") + ".20" + Byte2Hex(Arrays.copyOfRange(recv, 12, 13)).replace(" ", ""));
			//Verfällt am
			verfall.setText(Byte2Hex(Arrays.copyOfRange(recv, 11, 12)).replace(" ", "") + "/" + Byte2Hex(Arrays.copyOfRange(recv, 10, 11)).replace(" ", ""));
			
			// EF_BÖRSE
			recv = transceive("00 B2 01 CC 00");
			// BLZ
			blz.setText(Byte2Hex(Arrays.copyOfRange(recv, 1, 5)).replace(" ", ""));
			// Kontonr.
			konto.setText(Byte2Hex(Arrays.copyOfRange(recv, 5, 10)).replace(" ", ""));
			
			recv = transceive("00 A4 04 00 0E 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00");
			int len = recv.length;
			if (len >= 2 && recv[len - 2] == 0x90 && recv[len - 1] == 0) {
				// PSE supported
				addAIDRow(getResources().getText(R.string.ui_pse), getResources().getText(R.string.text_yes));
			} else {
				// no PSE
				addAIDRow(getResources().getText(R.string.ui_pse), getResources().getText(R.string.text_no));
			}
			
			tagcomm.close();
		} catch (IOException e) {
			toastError(getResources().getText(R.string.error_nfc_comm_cont) + (e.getMessage() != null ? e.getMessage() : "-"));
		}
	}
	
	protected byte[] transceive(String hexstr) throws IOException {
		String[] hexbytes = hexstr.split("\\s");
		byte[] bytes = new byte[hexbytes.length];
		for (int i = 0; i < hexbytes.length; i++) {
			bytes[i] = (byte) Integer.parseInt(hexbytes[i], 16);
		}
		log("Send: " + Byte2Hex(bytes));
		byte[] recv = tagcomm.transceive(bytes);
		log("Received: " + Byte2Hex(recv));
		return recv;
	}
    
    protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		
		switch (id) {
		case DIALOG_NFC_NOT_AVAIL:
			dialog = new AlertDialog.Builder(this)
			.setMessage(getResources().getText(R.string.error_nfc_not_avail))
			.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ECInformationGrabberActivity.this.finish();
				}
			})
			.create();
			break;
		default:
			dialog = null;
		break;		
		}
		
		return dialog;
	}
    
    private void addAIDRow(CharSequence left, CharSequence right) {
		TextView t1 = new TextView(this);
		t1.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		t1.setPadding(0, 0, (int) (getResources().getDisplayMetrics().density * 10 + 0.5f), 0);
		t1.setTextAppearance(this, android.R.attr.textAppearanceMedium);
		t1.setText(left);
		
		TextView t2 = new TextView(this);
		t2.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		t2.setText(right);
		
    	TableRow tr = new TableRow(this);
		tr.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		tr.addView(t1);
		tr.addView(t2);

		TableLayout t = (TableLayout) findViewById(R.id.table_features);
		t.addView(tr, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

	protected void log(String msg) {
		Log.d(LOGTAG, msg);
	}
	
	protected void toastError(CharSequence msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}
	
	private String Byte2Hex(byte[] input) {
		StringBuilder result = new StringBuilder();
		
		for (Byte inputbyte : input) {
			result.append(String.format("%02X ", inputbyte));
		}
		return result.toString();
	}
}