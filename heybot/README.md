# heybot

###### Purpose:

**heybot** is my best friend on my daily development activities. It's designed to help developers on their day-to-day development activities. It eases *some* chore development activities and improves your productivity. *It's worth trying it.*

###### Requirements:

**heybot** is mainly designed to work in **subversion** and **redmine** environment. If you use [redmine](http://www.redmine.org/) as project management application and [subversion](https://subversion.apache.org/) as version control system, then **heybot** can help you. For other ecosystems, you can fork the project or contribute directly to this project. All useful contributions are wellcome.

###### Usage:

It's very easy to use **heybot** and its call syntax is like commanding a bot. It's as easy as saying this:

```
$ heybot -do something.hb
```

This is the main syntax and now you've learned most of it. You write details into a file with extension **.hb**. Then you say to **heybot**:
	
> Hey bot! Do my job which is defined in the file I gave.
	
For example; I want to begin a redmine issue with creating a branch and checking it out to my workspace, I call this:
	
```
$ heybot -do begin_issue.hb
```

As a common practice a **.hb** file starts with parameter *OPERATION*  and it is followed by required parameters to do that operation metioned. In [workspace](https://github.com/csonuryilmaz/utilities/tree/master/heybot/workspace) you can find example **.hb** templates for each operation. You can copy them, and define your own operation by filling parameters. Also it's a best practice to give you **.hb** files a readable name. For ex: send_local_changes_to_test_env.hb

Let's dive into some details and explain them with examples. Below there is a list of operations that you can use with heybot.
	
**1. OPERATION= Upload** 

It uploads local changes in the working copy (output of *svn st* command) to a remote server by SFTP protocol.

Required parameters:

- HOST= IP of remote server to connect.
- USERNAME= Username to login remote server.
- PASSWORD= Password to login remote server.
- REMOTE_PATH= Remote working directory to send changes.

Optional parameters:

- SOURCE_PATH= Local working directory to take changes from. If not given or empty, *current working directory* is assumed. (pwd)
	
**2. OPERATION= Cleanup**

It deletes *closed* issues (branches) from local working directory and subversion. It is meaningful if you use *one issue is resolved in one branch* paradigm.

Required parameters:

- LOCAL_PATH= Branch local working directory used as workspace.
- SUBVERSION_PATH= Branch subversion directory where all branches are kept.
- REDMINE_TOKEN= Redmine API access key taken from [my account page](http://www.redmine.org/projects/redmine/wiki/RedmineAccounts).
- REDMINE_URL= Redmine API url. (most of the time this is root url of your redmine installation)

Optional parameters:

- LIMIT= Maximum count to delete branches. If not given or empty, *unlimited* is assumed.
	
**3. Deploy**

*todo*



**things to do on my day off**

- [ ] Send new version is deployed e-mail to some recipients like newsletter when a version is deployed. (after deploy operation)
- [ ] Auto sync start date (in progress),end date (deployed/closed) and *status* with related issues. Support <--> Other projects (web,mobile,cronint)
- [ ] Auto add related issue's assignee to other related issue's watcher list. Support <--> Other projects (web,mobile,cronint)
- [ ] When a branch is detected in a repository then auto-start the related issue. (in progress)