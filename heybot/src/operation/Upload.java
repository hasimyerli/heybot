package operation;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import utilities.Properties;
import java.util.Vector;

/**
 * Operation: upload
 *
 * @author onur
 */
public class Upload extends Operation
{

    private final static int SFTPPORT = 22;

    //<editor-fold defaultstate="collapsed" desc="parameters">
    // mandatory
    private final static String PARAMETER_HOST = "HOST";
    private final static String PARAMETER_USERNAME = "USERNAME";
    private final static String PARAMETER_PASSWORD = "PASSWORD";
    private final static String PARAMETER_REMOTE_PATH = "REMOTE_PATH";
    // optional
    private final static String PARAMETER_SOURCE_PATH = "SOURCE_PATH";

    //</editor-fold>
    public Upload()
    {
	super(new String[]
	{
	    PARAMETER_HOST, PARAMETER_USERNAME, PARAMETER_PASSWORD, PARAMETER_REMOTE_PATH
	}
	);
    }

    @Override
    public void execute(Properties prop)
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    String sftpHost = getParameterString(prop, PARAMETER_HOST, false);
	    String sftpUser = getParameterString(prop, PARAMETER_USERNAME, false);
	    String sftpPass = getParameterString(prop, PARAMETER_PASSWORD, false);
	    String sftpTargetDir = getParameterString(prop, PARAMETER_REMOTE_PATH, false);
	    String sftpSourceDir = getParameterString(prop, PARAMETER_SOURCE_PATH, false);
	    String revision = "";// @todo

	    if (sftpSourceDir == null || sftpSourceDir.length() == 0)
	    {// try to assign default argument
		sftpSourceDir = tryExecute("pwd");
	    }
	    String svnCommand = tryExecute("which svn");
	    // ---- some log
	    System.out.println("Using default SFTP port: " + SFTPPORT);
	    System.out.println("Using --source-directory: " + sftpSourceDir);
	    System.out.println("Using SVN command:" + svnCommand);

	    if (svnCommand.length() == 0)// check svn command
	    {
		System.err.println("Ooops! Couldn't find SVN command.");
	    }
	    else if (sftpSourceDir.length() == 0)// check source directory
	    {
		System.err.println("Ooops! Couldn't find/detect any --source-directory.");
	    }
	    else
	    {
		String repoRootDir = tryGetRepoRootDir(svnCommand, sftpSourceDir);
		if (repoRootDir.length() == 0)// check repo root dir
		{
		    System.err.println("Ooops! Couldn't get repository Relative URL.");
		}
		else
		{
		    // ---- some log
		    System.out.println("Using repo relative URL: " + repoRootDir);
		    // let's send changes baby :)
		    start(sftpHost, sftpTargetDir, sftpUser, sftpPass, sftpSourceDir, revision, svnCommand, repoRootDir);
		}
	    }
	}
    }

    //<editor-fold defaultstate="collapsed" desc="SFTP SEND">
    private void start(String sftpHost, String sftpTargetDir, String sftpUser, String sftpPass, String sftpSourceDir, String revision, String svnCommand, String repoRootDir)
    {
	System.out.println();
	System.out.println("==========================[ SVN ]==========================");
	String changes;
	boolean absPath = true;
	if (revision != null && revision.length() > 0)
	{// specific revision
	    changes = tryExecute(svnCommand + " log -v -r " + revision + " " + sftpSourceDir);
	    System.out.println(changes);
	    // trim trash data from changes
	    String pattern = "Changed paths:";
	    int i = changes.indexOf(pattern);
	    if (i >= 0)
	    {
		changes = changes.substring(i + pattern.length());
		absPath = false;
	    }
	    else
	    {
		System.out.println("There is no change in revision to upload!");
		System.exit(0);
	    }
	}
	else
	{
	    changes = tryExecute(svnCommand + " st " + sftpSourceDir);
	    absPath = true;
	    System.out.println(changes);
	}
	System.out.println("==========================[ FTP ]==========================");
	upload(changes, absPath, sftpHost, sftpUser, sftpPass, sftpTargetDir, sftpSourceDir, repoRootDir);
    }

    private void upload(String changes, boolean absPath, String sftpHost, String sftpUser, String sftpPass, String sftpTargetDir, String sftSourceDir, String repoRootDir)
    {
	if (!sftSourceDir.endsWith("/"))
	{// append last /
	    sftSourceDir += "/";
	}

	Session session = null;
	Channel channel = null;
	ChannelSftp channelSftp = null;
	try
	{

	    JSch jsch = new JSch();
	    session = jsch.getSession(sftpUser, sftpHost, SFTPPORT);
	    session.setPassword(sftpPass);
	    java.util.Properties config = new java.util.Properties();
	    config.put("StrictHostKeyChecking", "no");
	    session.setConfig(config);
	    session.connect();
	    channel = session.openChannel("sftp");
	    channel.connect();
	    channelSftp = (ChannelSftp) channel;
	    channelSftp.cd(sftpTargetDir);

	    String[] rows = changes.split("\n");
	    String operation, fileordir;
	    for (String row : rows)
	    {
		// check first column
		if (row.length() > 0 && row.substring(0, 1).equals(" "))
		{// empty! (only directory metadata change)
		    continue;
		}

		row = row.trim();
		if (row.length() == 0)
		{// pass empty rows
		    continue;
		}

		int fs = row.indexOf("/");
		if (fs > 0)
		{
		    // check first column for operation
		    operation = row.substring(0, 1);
		    fileordir = row.substring(fs).trim();

		    int ei = fileordir.indexOf("(");
		    if (ei > 0)
		    {
			fileordir = fileordir.substring(0, ei).trim();
		    }

		    if (!absPath)
		    {// modify path to be absolute local path
			fileordir = sftSourceDir + fileordir.replace(repoRootDir, "");
		    }

		    if (operation.equals("A") || operation.equals("?"))
		    {// addition || not under version control
			insert(channelSftp, fileordir, sftSourceDir);
		    }
		    else if (operation.equals("M") || operation.equals("R"))
		    {// modified || replaced
			insert(channelSftp, fileordir, sftSourceDir);
		    }
		    else if (operation.equals("D") || operation.equals("!"))
		    {// deleted || missing
			delete(channelSftp, fileordir, sftSourceDir);
		    }
		    else
		    {// unknown operation || end of modifications
			break;
		    }
		}
		else
		{// end of modifications
		    break;
		}
	    }

	    channelSftp.disconnect();
	    channel.disconnect();
	    session.disconnect();
	}
	catch (JSchException | SftpException | FileNotFoundException ex)
	{
	    System.err.println(System.getProperty("line.separator") + "Ooops! Error while sending files. (" + ex.getMessage() + ")");
	}
    }

    private void insert(ChannelSftp channelSftp, String path, String sftSourceDir) throws SftpException, FileNotFoundException
    {
	System.out.println("[I] " + path);

	File f = new File(path);
	path = path.replace(sftSourceDir, "");// relative path
	if (f.isDirectory())
	{
	    makeDir(channelSftp, path);
	    for (String child : f.list())
	    {
		insert(channelSftp, f.getPath() + "/" + child, sftSourceDir);
	    }
	}
	else
	{
	    //System.out.println(channelSftp.realpath(path));
	    channelSftp.put(new FileInputStream(f), path, new UploadProgressMonitor(), ChannelSftp.OVERWRITE);
	}
    }

    private void delete(ChannelSftp channelSftp, String path, String sftSourceDir) throws SftpException
    {
	System.out.print("[D] " + path);

	path = path.replace(sftSourceDir, "");// relative path

	int id = path.lastIndexOf(".");
	int is = path.lastIndexOf("/");
	if (id < 0 || is > id)
	{// directory
	    rmDir(channelSftp, path);
	}
	else
	{
	    delFile(channelSftp, path);
	    System.out.println("  [✓]");
	}
    }

    private void makeDir(ChannelSftp channelSftp, String path) throws SftpException
    {
	try
	{
	    SftpATTRS attr = channelSftp.stat(path);
	}
	catch (SftpException ex)
	{
	    if (ex.id == 2)
	    {// no such directory
		int i = path.lastIndexOf("/");
		if (i > 0)
		{// has parent
		    makeDir(channelSftp, path.substring(0, i));
		}
		channelSftp.mkdir(path);// make dir on remote
	    }
	}
    }

    private void rmDir(ChannelSftp channelSftp, String path) throws SftpException
    {
	try
	{
	    channelSftp.cd(path);
	}
	catch (SftpException ex)
	{
	    if (ex.id == 2)
	    {// No such file!
		// ignore
		return;
	    }
	    else
	    {// Unknown exception
		throw ex;
	    }
	}

	String[] directories = listDirectories(channelSftp);
	String[] files = listFiles(channelSftp);
	for (int i = 0; i < directories.length; i++)
	{
	    rmDir(channelSftp, directories[i]);
	}
	for (int i = 0; i < files.length; i++)
	{
	    delFile(channelSftp, files[i]);
	}

	channelSftp.cd("..");

	delDir(channelSftp, path);
    }

    private String[] listFiles(ChannelSftp channelSftp) throws SftpException
    {
	return listFiles(channelSftp, ".");
    }

    private String[] listFiles(ChannelSftp channelSftp, String path) throws SftpException
    {
	return listDirectory(channelSftp, path, true, false);
    }

    private String[] listDirectories(ChannelSftp channelSftp) throws SftpException
    {
	return listDirectory(channelSftp, ".", false, true);
    }

    private String[] listDirectory(ChannelSftp channelSftp, String path, boolean includeFiles, boolean includeDirectories)
	    throws SftpException
    {

	@SuppressWarnings("unchecked")
	Vector<String> vv = channelSftp.ls(path);
	if (vv != null)
	{
	    List<String> ret = new ArrayList<String>();
	    for (int i = 0; i < vv.size(); i++)
	    {
		Object obj = vv.elementAt(i);
		if (obj instanceof com.jcraft.jsch.ChannelSftp.LsEntry)
		{
		    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) obj;
		    if (includeFiles && !entry.getAttrs().isDir())
		    {
			ret.add(entry.getFilename());
		    }
		    if (includeDirectories && entry.getAttrs().isDir())
		    {
			if (!entry.getFilename().equals(".") && !entry.getFilename().equals(".."))
			{
			    ret.add(entry.getFilename());
			}
		    }
		}
	    }
	    return ret.toArray(new String[ret.size()]);
	}

	return null;
    }

    private void delDir(ChannelSftp channelSftp, String path) throws SftpException
    {
	try
	{
	    channelSftp.rmdir(path);
	}
	catch (SftpException ex)
	{
	    if (ex.id == 2)
	    {// No such file!
		// ignore
	    }
	    else
	    {// Unknown exception
		throw ex;
	    }
	}
    }

    private void delFile(ChannelSftp channelSftp, String path) throws SftpException
    {
	try
	{
	    channelSftp.rm(path);
	}
	catch (SftpException ex)
	{
	    if (ex.id == 2)
	    {// No such file!
		// ignore
	    }
	    else
	    {// Unknown exception
		throw ex;
	    }
	}
    }
//</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="sftp put progress monitor">
    public class UploadProgressMonitor implements SftpProgressMonitor
    {

	public UploadProgressMonitor()
	{
	}

	public void init(int op, java.lang.String src, java.lang.String dest, long max)
	{
	    //System.out.println("STARTING: " + op + " " + src + " -> " + dest + " total: " + max);
	    System.out.print("|-> ");
	}

	private long total;

	public boolean count(long bytes)
	{
	    total += bytes;
	    return (true);
	}

	public void end()
	{
	    System.out.println(" " + total + " bytes [✓]");
	}
    }

//</editor-fold>
}
