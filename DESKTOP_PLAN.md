Trying to figure out a way to prevent our pronblocker on desktop from just being something you can shut down i.e. you go into cmd prmpt and from just typing in taskkill /F /PID 15256 /PID 7320 /PID 18328
 into cmd prompt what are the options? and same with task scheduler.

heres my research, i think weve done some of these already:

Method	Blocks taskkill /F?	Blocks Admin?	Complexity	Risk
Process ACL	Yes (user-mode)	No (taken ownership)	Low	Low
Kernel Driver (ObRegisterCallbacks)	Yes	Yes (standard tools)	High	Driver signing required
BreakOnTermination	Yes (causes BSOD)	Yes	Medium	System crash on kill
Blocking Task Manager / cmd	Yes (indirectly)	Depends on implementation	Medium	Can lock out user
Service Watchdog	Restarts after kill	Yes	Low	Can be defeated by renaming
