WikiSayIt is a free and open-source Android app for recording pronunciations and contributing the recordings to Wikimedia Commons and then linking to those recordings from items or lexemes in Wikidata. It is inspired by the browser-based Lingua Libre site.

Users select lists of words, names, or phrases to pronounce, are shown them one at a time, pronounce each, review the recorded pronunciations (unselecting or re-recording ones that they're unhappy with), and upon confirming an approved 

## Architecture and Tech Stack

* **Frontend Framework:** Jetpack Compose (Kotlin) for native Android UI
* **Recording infrastructure:** auto-cropping silence and background noise, then adding 0.2 seconds of silence on either end of the recording, converting to Ogg Vorbis. Inspiration can be drawn from LinguaRecorder (https://github.com/lingua-libre/LinguaRecorder.git and also available at ~/dev/LinguaRecorder)
* **Wiki client code:** use Mediawiki REST API for both Wikimedia Commons and Wikidata
* **User interface:** use I18N for everything. All strings should be localizable using Translatewiki.net. All user-interface should be available in both left-to-right and right-to-left layout. When the user selects a right-to-left interface language, the layout in all screens should change to right-to-left layout.

## App flow
* First, the user is invited to create a new speaker profile or select an existing profile. (In a Settings screen, allow users to set a checkbox for automatically using the last used profile by default.)
  * the profile includes a Wikimedia username, which the app logs in with, to Commons and to Wikidata using OAuth. This is mandatory. 
  * the profile also includes language sets: a language name and ISO code, a proficiency level ('native' or 'proficient'), and a dialect description (empty for 'standard', otherwise a dialect name or a region)

* after selecting a profile, user selects a language for recording from among those listed in their profile.
* next, the user select a source for the list of words or phrases to record. Possible sources:  
  ** users can paste their own lists (one word/name/phrase per line) and specify whether those are supposed to match Wikidata item labels (including being offered a choice to disambiguate as necessary before recording) or Wikidata lexemes (liekwise disambiguation may be necessary)
  ** users can paste a Wikidata query returning items or lexemes (remember to collect the *default* Wikidata label if a label doesn't exist in the language the user selected for recording)
  ** users can specify a category name from a Wikipedia in a given language (default to the selected recording language) and optionally traversing sub-directories as well, to a maximum of five levels, and with circularity detection to avoid hangs.
* once a list is available, show a button to proceed to checking recordings existence (and a smaller, secondary button allowing the user to skip the test and use the list as-is). For each entry in the list, check as follows: For entries that are Wikidata items, check whether they have the "pronunciation audio" property (P443). If they do, exclude them from the list (broadly, the app's main goal is to add recordings that are still missing entirely). For entries that are Lexemes, check whether EACH of the forms of the lexeme has the P443 property. If the lexeme has no forms, or if all its forms already have P443, exclude them from the list. If there are any forms without P443, add each such form to the list for recording (also storing its containing lexeme, which will be used for constructing the filename later).
* when the final list is ready, show the user a START button. When clicked, start a recording session: for each entry in the list:
  ** show the entry in a large font at screen center. At screen bottom, show a Skip button, which instantly removes the entry from the list and moves on.
  ** record the user pronouncing it. (the app should show very clear indication of when recording is active.) After the user says something, interpret more than 1.5 seconds of silence as a cue to stop recording.
  ** clean up the recording as is done in LinguaRecorder.
  ** show the next word, unless the user clicked the Redo button during speaking or during the 1.5 seconds of silence. If Redo was clicked, keep the same word and start a new recording.
  ** continue until all words in the list have been recorded, or the user clicked a stop button.
* review phase. For each recorded word/phrase, show the word/phrase at center of screen and play the cleaned-up audio that was recorded, along with a Redo button. After playing, pause 1.5 seconds to allow the user to click Redo or Skip; if the user clicks Skip, remove the entry from the list and proceed; if the user clicked Redo, queue the word for re-recording; either way, proceed to switch the display to the next recorded word/phrase, and play it for review, until the whole list was reviewed.
* if any words were queued for redoing, re-record those words in the recording flow, and then proceed to review just those queued words.
* eventually, there is a list of recorded and approved words. Show the total count, and a button called "Contribute to Commons and Wikidata". Under the button add "small print" reminding users the recording will be published under the Creative Commons Zero (CC0) license and may be used for any purpose. Also show a secondary button "abandon session", which, after confirmation, would end the recording session, deleting the on-device recordings, and return to the first view (profile selection).
* Once the "Contribute" button was pressed, switch to showing a progress bar with the number of entries contributed out of the total approved list. Each recording should be: 
** uploaded to Commons with a set name scheme: recordings of Wikidata lexeme titles should follow the pattern "<iso lang code>-<LID>-<entry title>-<username>.ogg". For example, a Ukrainian lexeme titled 'мова' recorded by a user whose Wikimedia username is 'Ijon' would be named "uk-L708539-мова-Ijon.ogg",and recordings of Wikidata item titles should follow the pattern "<iso lang code>-<QID>-<item title>-<username>.ogg". For example, the Hebrew name of the Wikidata item about the poet Yonatan Ratosh would be uploaded as "he-Q432522-יונתן רטוש-Ijon.ogg".  The files should be uploaded with two Commons categories as well: "Category:WikiSayIt pronunciations: <iso code>" (e.g. "Category:WikiSayIt pronunciations: he") and "Category:WikiSayIt pronunciations by <username>" (e.g. "Category:WikiSayIt pronunciations by Ijon")
** Once the file is on Commons, update the Wikidata entry (item or lexeme), adding a P443 property whose value points at the file just uploaded. Wikidata items should get the P443 statement at the item level, and lexemes should get the P443 statement at the Form level of the specific Form that was recorded. For example, a recording of the English noun 'water' (lexeme ID L3302) in its singular form 'water' would be added under L3302-F1. See here on Wikidata: https://www.wikidata.org/wiki/Lexeme:L3302
* Once the full list of approved recordings has been contributed, show the user buttons taking them to their Commons contribution history view and their Wikidata contribution history view, in case they want to review it, plus a home button that takes them back to profile selection to start a new recording session.

## Beyond core flow
* At the top of the screen, always show the app name (WikiSayIt) and a hamburger menu, containing a Settings, a Stats, and an About option. Clicking About should show the contents of the CREDITS file. 
* In Stats view, show the number of recordings made, sub-divided by Wikidata items versus Wikidata lexeme forms, also totaled per month and year.

