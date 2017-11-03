# TheCashster

TheCashster is a simple Expense tracking software for Android. It is meant as a companion to [TheBankster](https://github.com/splitbrain/TheBankster) but should work fine alone.
 
The goal is to keep expense tracking as simple as possible: 

1) pick the place you're at
2) enter the amount you spent
3) click *Done* - because you are

There are no categories to pick, no budgets to create or anything complicated. All your expenses are logged to a Google Sheets spreadsheet - that's where you can do whatever complicated reporting you want.

TheCashster is available on Google Play and on Github.

## Permissions

The application needs a few permissions. Here's what they are used for.

* `ACCESS_FINE_LOCATION` You will be asked for permission to "access this device's location" when you start the app. It is required to locate where you are and suggest nearby places.
* `GET_ACCOUNTS` You will be asked to allow to "access your contacts" when you save your first expense. This is required to access your phone's Google account which is used to authenticate at the Google Sheets API.
* `INTERNET` This permission is granted on installation and allows the app to access the internet to talk to Foursquare and Google.
* `VIBRATE` This enables the haptic feedback when clicking the number buttons. The permission is granted on installation.

## Places

When starting up the app, a list of nearby places will appear automatically based on your current location. The places are provided by [FourSquare](https://foursquare.com).

Select the place you're at and you're ready to submit an expense. Places that have been used by you are stored on your device and will be suggested at the top next time you're near. You can recognize those places by the little star ★ symbol.

You can delete locally stored ★ places by long pressing on them and confirm the deletion.

If you can't find what you're looking for, use the search at the top of the screen. It is used to request places matching what you entered. You will also get the opportunity to simply create a local ★ place based on your input.

## Google Sheets

The moment you save your first expense, TheCashster will create a new [Google Sheets](https://docs.google.com/spreadsheets/) spreadsheet for you. Your installation is then associated with this spreadsheet and all expenses will be logged to it.
  
Because the spreadsheet is identified by an internal ID you're free to rename or move it. You can also edit the formatting, rename the column headers or add additional sheets. You can safely remove rows if you accidentally tracked a wrong expense.

You should however not change the order of the columns and you have to keep in mind that data is tracked on the very first sheet of the document.

There is currently no way to associate TheBankster with an existing spreadshee. This means whenever you reinstall the app it will create a new spreadsheet and there is no way to have the app on two devices track to the same spreadsheet. Support for that might come later.

## License

TheCashster is Open Source and licensed under the MIT License

Copyright 2017 Andreas Gohr

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.