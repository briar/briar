Set Shell = CreateObject("WScript.Shell")
Shell.Run "briar.tmp\jre\bin\javaw -ea -jar briar.exe", 0, true
Set Shell = Nothing
Set Fso = CreateObject("Scripting.FileSystemObject")
Fso.DeleteFolder "briar.tmp"
Set Fso = Nothing

