package de.blinkt.openvpn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import de.blinkt.openvpn.VPNConfigPreference.VpnPreferencesClickListener;

public class VPNProfileList extends PreferenceFragment implements  VpnPreferencesClickListener {
	private static final int MENU_ADD_PROFILE = Menu.FIRST;

	private static final int START_VPN_PROFILE= 70;
	private static final int START_VPN_CONFIG = 92;


	private VpnProfile mSelectedVPN;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.vpn_profile_list);
		setHasOptionsMenu(true);
		refreshList();
		// Debug load JNI
		OpenVPN.foo();
	}


	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.add(0, MENU_ADD_PROFILE, 0, R.string.menu_add_profile)
		.setIcon(android.R.drawable.ic_menu_add)
		.setAlphabeticShortcut('a')
		.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM
				| MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int itemId = item.getItemId();
		if (itemId == MENU_ADD_PROFILE) {
			onAddProfileClicked();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	private void askForPW(String type) {

		final EditText entry = new EditText(getActivity());
		entry.setSingleLine();
		entry.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);

		AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
		dialog.setTitle("Need " + type);
		dialog.setMessage("Enter the password for profile " + mSelectedVPN.mName);
		dialog.setView(entry);

		dialog.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String pw = entry.getText().toString();
				mSelectedVPN.mTransientPW = pw;
				onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);

			}

		});
		dialog.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			}
		});

		dialog.create().show();

	}

	private void onAddProfileClicked() {
		Context context = getActivity();
		if (context != null) {
			final EditText entry = new EditText(context);
			entry.setSingleLine();

			AlertDialog.Builder dialog = new AlertDialog.Builder(context);
			dialog.setTitle(R.string.menu_add_profile);
			dialog.setMessage(R.string.add_profile_name_prompt);
			dialog.setView(entry);


			dialog.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String name = entry.getText().toString();
					if (getPM().getProfileByName(name)==null) {
						VpnProfile profile = new VpnProfile(name);
						addProfile(profile);
						refreshList();
					} else {
						Toast.makeText(getActivity(), R.string.duplicate_profile_name, Toast.LENGTH_LONG).show();
					}
				}


			});
			dialog.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
				}
			});
			dialog.create().show();
		}

	}


	private void addProfile(VpnProfile profile) {
		getPM().addProfile(profile);
		getPM().saveProfileList(getActivity());
		getPM().saveProfile(getActivity(),profile);

	}





	public void refreshList() {
		PreferenceScreen plist = getPreferenceScreen();
		if (plist != null) {
			plist.removeAll(); 
			getPM().loadVPNList(getActivity());

			for (VpnProfile vpnprofile: getPM().getProfiles()) {

				String profileuuid = vpnprofile.getUUID().toString();


				VPNConfigPreference vpref = new VPNConfigPreference(this);
				vpref.setKey(profileuuid);
				vpref.setTitle(vpnprofile.getName());
				vpref.setPersistent(false);
				vpref.setOnQuickSettingsClickListener(this);
				plist.addPreference(vpref);
			}


		}
	}



	private ProfileManager getPM() {
		return ProfileManager.getInstance();
	}

	@Override
	public boolean onQuickSettingsClick(Preference preference) {
		String key = preference.getKey();

		VpnProfile vprofile = ProfileManager.get(key);

		Intent vprefintent = new Intent(getActivity(),VPNPreferences.class)
		.putExtra(getActivity().getPackageName() + ".profileUUID", vprofile.getUUID().toString());

		startActivityForResult(vprefintent,START_VPN_CONFIG);
		return true;
	}





	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode==START_VPN_PROFILE && resultCode == Activity.RESULT_OK) {
			
			if(mSelectedVPN.needUserPWInput()!=null) {
				askForPW(mSelectedVPN.needUserPWInput());
			} else {
				new startOpenVpnThread().start();
			}

		} else if (requestCode == START_VPN_CONFIG && resultCode == Activity.RESULT_OK) {
			String configuredVPN = data.getStringExtra(getActivity().getPackageName() + ".profileUUID");

			VpnProfile profile = ProfileManager.get(configuredVPN);
			getPM().saveProfile(getActivity(), profile);
			// Name could be modified
			refreshList();
		}

	}

	private class startOpenVpnThread extends Thread {

		@Override
		public void run() {
			startOpenVpn();
		}

		void startOpenVpn() {
			Intent startLW = new Intent(getActivity().getBaseContext(),LogWindow.class);
			startActivity(startLW);


			OpenVPN.logMessage(0, "", "Building configration...");

			Intent startVPN = mSelectedVPN.prepareIntent(getActivity());

			getActivity().startService(startVPN);
			getActivity().finish();

		}
	}

	void showConfigErrorDialog(int vpnok) {

		AlertDialog.Builder d = new AlertDialog.Builder(getActivity());

		d.setTitle(R.string.config_error_found);

		d.setMessage(vpnok);


		d.setPositiveButton("Ok", null);

		d.show();
	}

	@Override
	public void onStartVPNClick(VPNConfigPreference preference) {
		getPM();
		// Query the System for permission 
		mSelectedVPN = ProfileManager.get(preference.getKey());

		int vpnok = mSelectedVPN.checkProfile();
		if(vpnok!= R.string.no_error_found) {
			showConfigErrorDialog(vpnok);
			return;
		}


		getPM().saveProfile(getActivity(), mSelectedVPN);
		Intent intent = VpnService.prepare(getActivity());

		if (intent != null) {
			// Start the query
			intent.putExtra("FOO", "WAR BIER");
			startActivityForResult(intent, START_VPN_PROFILE);
		} else {
			onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);
		}

	}
}