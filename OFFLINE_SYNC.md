# Two-way sync: PC <-> phones (through the internet)

Both apps save everything on the device first, so work never stops when internet is down.
When internet comes back:

- **PC -> phones:** sales, returns, stock movements, products, customers/suppliers, business days, holidays
  and the audit log made on the PC are uploaded to the cloud store. Phones receive them live.
- **Phones -> PC:** whatever phones put in the cloud is downloaded by the PC and the screen refreshes
  (it waits if someone is typing).
- The PC retries every 20 seconds while anything is waiting. Phones retry every 2 minutes and on reconnect.
- Wi-Fi sync between phone and PC (same network, no internet) still works too.

## One-time setup

1. **Firebase console** -> Authentication -> Sign-in method -> turn on **Email/Password**.
2. **PC app** -> menu **Cloud Sync -> Cloud Sync setup...**
   - Type the owner's Google email (the one used to sign in on the phone).
   - If that account never had a password, press **Send password email**, open the email, choose a password.
   - Type the password and press **Save & connect**. The window shows "Connected" and how many items are waiting.
3. The phone must be signed in with Google on that same owner account (as it already is).

## Good to know

- Users and passwords are NOT sent to the cloud (the cloud never stores them). New users move between PC and
  phone by the Wi-Fi sync, as before.
- Cloud sync uses the store owner's account. If the setup window shows "permission-denied", the email is not the
  store owner's.
- If it shows "API key not valid", get the Web API key from Firebase console -> Project settings and paste it
  into the optional field in the setup window.
- Phones: changes made while signed in only with the local login (omini / admin116) wait until that phone signs
  in with Google once.
- Deleting a holiday stays local.
