/**
 * Copyright 2012 Andrew Okin
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package forkk.multimc.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import forkk.multimc.compat.OS;
import forkk.multimc.compat.OSUtils;
import forkk.multimc.data.exceptions.InstanceInitException;
import forkk.multimc.data.exceptions.InstanceSaveException;
import forkk.multimc.settings.AppSettings;

public class Instance
{
	// Constants
	/**
	 * Invalid characters that aren't allowed in an instance's name.
	 */
	public static final String InvalidNameChars = "<>\n\\/&.";
	
	/**
	 * The name of the file used to store an instance's information
	 */
	public static final String InstanceDataFileName = "instance.xml";
	
	// Methods
	public Instance(String name, String rootDir) throws InstanceInitException
	{
		Init(rootDir);
		setName(name);
	}
	
	public Instance(String rootDir) throws InstanceInitException
	{
		Init(rootDir);
	}
		
	private void Init(String rootDir) throws InstanceInitException
	{
		this.rootDir = rootDir;
		if (!new File(rootDir).exists())
		{
			new File(rootDir).mkdir();
		}
		if (!new File(rootDir).exists())
		{
			throw new InstanceInitException("Failed to " +
					"initialize instance because it's root directory could " +
					"not be created.");
		}
		
		try
		{
			autosave = true;
			installTimes = loadInstallTimes();
			docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			
			if (getInstDataFile().exists())
				xmlDoc = docBuilder.parse(getInstDataFile());
			else
				xmlDoc = docBuilder.newDocument();
		} catch (ParserConfigurationException e)
		{
			e.printStackTrace();
			throw new InstanceInitException("Failed to load the instance " +
					"because the XML parser was misconfigured.");
		} catch (SAXException e)
		{
			e.printStackTrace();
			throw new InstanceInitException("Failed to parse the instance's " +
					"XML file. " + e.getMessage());
		} catch (IOException e)
		{
			e.printStackTrace();
			throw new InstanceInitException("Unknown IO exception when " +
					"loading instance. " + e.getMessage());
		}
	}
	
	// Fields
	/**
	 * The XML document
	 */
	Document xmlDoc;
	DocumentBuilder docBuilder;
	
	/**
	 * The instance's root directory
	 */
	String rootDir;
	
	/**
	 * If true, the XML document will save automatically.
	 */
	boolean autosave;
	
	/**
	 * When using the symbolic link launch method, this will keep track of what
	 * the original .minecraft folder has been renamed to.
	 */
	File origMCTemp;
	
	/**
	 * The instance's process
	 */
	Process instProc;
	
//	boolean symlinkLaunch;
	
	public void Launch()
	{
//		this.symlinkLaunch = symlinkLaunch;
		
		String homeParam = this.getRootDir().getAbsolutePath();
		homeParam = (homeParam.contains(" ")? "\"" + homeParam + "\"" : homeParam);
		
		ProcessBuilder mcProcBuild = new ProcessBuilder(
				new File(new File(System.getProperty("java.home"), "bin"), "java").toString(),
				"-Duser.home=" + homeParam,
				"-Xms" + AppSettings.getInitialMemAlloc() + "m",
				"-Xmx" + AppSettings.getMaxMemAlloc() + "m",
				"-cp", 
				AppSettings.getLauncherFilename(),
				"net.minecraft.LauncherFrame"
				);
		
		String cmdString = "";
		
		for (String str : mcProcBuild.command())
		{
			cmdString += str + " ";
		}
		
		System.out.println("Launching with command: " + cmdString);
		
		if (OSUtils.getCurrentOS() == OS.WINDOWS)
		{
			mcProcBuild.environment().put("APPDATA", rootDir);
		}
		
//		switch (OSUtils.getCurrentOS())
//		{
//		case WINDOWS:
//			if (!symlinkLaunch)
//			{
//				mcProcBuild.environment().put("APPDATA", rootDir);
//				break;
//			}
//			
//		case MAC:
//		case LINUX:
//		default:
//			symlinkLaunch = true;
//			SymlinkLaunchPrep(mcProcBuild);
//			break;
//		}
		
		try
		{
			//mcProcBuild.inheritIO();
			instProc = mcProcBuild.start();
			
			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					Scanner scan = new Scanner(instProc.getInputStream());
					while (isRunning() || scan.hasNextLine())
					{
						if (scan.hasNextLine())
							System.out.println("Instance: " + scan.nextLine());
						try { Thread.sleep(250); } catch (InterruptedException e) { }
					}
					onProcExit();
				}
			}).start();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Called when Minecraft closes
	 */
	public void onProcExit()
	{
		System.out.println("Process quit.");
//		if (symlinkLaunch)
//			SymlinkLaunchEnd();
	}
	
	public boolean isRunning()
	{
		try
		{
			instProc.exitValue();
		} catch (IllegalThreadStateException e)
		{
			return true;
		}
		return false;
	}
	
	public Process getProcess()
	{
		return instProc;
	}
	
/*
	/**
	 * Launches the instance using symbolic links
	 */
/*
	private void SymlinkLaunchPrep(ProcessBuilder mcProcBuild)
	{
		File originalMC = OSUtils.getMinecraftDir();
		origMCTemp = getAvailableMCTemp();
		
		System.out.println(String.format("Renaming %1$s to %2$s", originalMC, origMCTemp));
		originalMC.renameTo(getAvailableMCTemp());
		
		File link = OSUtils.getMinecraftDir().toPath();
		File linkTarget = getMCDir().getAbsoluteFile();
		if (!linkTarget.exists())
			linkTarget.mkdirs();
		
		System.out.println(String.format("Creating link at %1$s to %2$s", link, linkTarget));
		try
		{
			Files.createSymbolicLink(link, linkTarget.toPath());
		} catch (UnsupportedOperationException e)
		{
			// TODO: handle exception
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Cleans up after a symbolic link launch. Does nothing if origMCTemp is
	 * null or doesn't exist.
	 */
/*
	private void SymlinkLaunchEnd()
	{
		if (Files.isSymbolicLink(OSUtils.getMinecraftDir().toPath()))
		{
			OSUtils.getMinecraftDir().delete();
		}
		if (origMCTemp != null && origMCTemp.exists())
		{
			origMCTemp.renameTo(OSUtils.getMinecraftDir());
		}
	}
	
	/**
	 * @return An available temporary file for the original .minecraft folder
	 * that is going to be replaced with a symbolic link.
	 */
/*
	private File getAvailableMCTemp()
	{
		final String mcTNBase = ".tempMC";
		File mcParent = new File(OSUtils.getMinecraftDir().getParent());
		File mcTemp = new File(mcParent, mcTNBase);
		if (mcTemp.exists())
		{
			for (int i = 0; (i < 99999999 && mcTemp.exists()); i++)
			{
				mcTemp = new File(mcParent, mcTNBase + "-" + i);
			}
		}
		return mcTemp;
	}
	*/
	
	// XML Stuff
	private void AutoSave()
	{
		if (autosave)
		{
			try
			{
				Save();
			} catch (InstanceSaveException e)
			{
				e.printStackTrace();
				return;
			}
		}
	}
	
	/**
	 * Saves the instance's XML document
	 * @throws InstanceSaveException If the instance fails to save for some reason
	 */
	public void Save() throws InstanceSaveException
	{
		try
		{
			if (!getRootDir().exists())
				getRootDir().mkdirs();
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			
			if (!getInstDataFile().exists())
				getInstDataFile().createNewFile();
			StreamResult result = new StreamResult(getInstDataFile());
			DOMSource source = new DOMSource(xmlDoc);
			transformer.transform(source, result);
		} catch (TransformerConfigurationException e)
		{
			e.printStackTrace();
			throw new InstanceSaveException("Instance failed to save because " +
					"the XML transformer was misconfigured.", this);
		} catch (TransformerFactoryConfigurationError e)
		{
			e.printStackTrace();
			throw new InstanceSaveException("Instance failed to save because " +
					"the XML transformer factory was misconfigured.", this);
		} catch (TransformerException e)
		{
			e.printStackTrace();
			throw new InstanceSaveException("Instance failed to save because " +
					"the XML transformer failed.", this);
		} catch (IOException e)
		{
			e.printStackTrace();
			throw new InstanceSaveException("Couldn't create instance data " +
					"file.", this);
		}
	}
	
	private Node getRootNode()
	{
		final String name = "instance";
		NodeList nodes = xmlDoc.getElementsByTagName(name);
		if (nodes.getLength() <= 0)
		{
			return xmlDoc.appendChild(xmlDoc.createElement(name));
		}
		else
		{
			return nodes.item(0);
		}
	}
	
	/**
	 * Returns the XML element with the given name. The element will be created
	 * if it doesn't exist
	 * @param element the name of the element to get
	 * @param parent the name of the element's parent
	 * @return the XML node
	 */
	private Node getXmlNode(String element)
	{
		return getXmlNode(element, null);
	}
	
	/**
	 * Returns the XML element with the given name. The element will be created
	 * if it doesn't exist
	 * @param element the name of the element to get
	 * @param parent the name of the element's parent
	 * @return the XML node
	 */
	private Node getXmlNode(String element, Element parent)
	{
		return getXmlNode(element, parent, null);
	}
	
	/**
	 * Returns the XML element with the given name. The element will be created
	 * if it doesn't exist
	 * @param element the name of the element to get
	 * @param parent the name of the element's parent
	 * @param defValue the default value of the new node
	 * @return the XML node
	 */
	private Node getXmlNode(String element, Element parent, String defValue)
	{
		if (parent == null)
			parent = (Element) getRootNode();
		NodeList nodes = parent.getElementsByTagName(element);
		if (nodes.getLength() <= 0)
		{
			if (defValue == null)
				return parent.appendChild(xmlDoc.createElement(element));
			else
			{
				Element newElement = xmlDoc.createElement(element);
				newElement.setTextContent(defValue);
				return parent.appendChild(newElement);
			}
		}
		else
		{
			return nodes.item(0);
		}
	}
	
	// File install times
	private static String installTimesFileName = "ModFileInstallTimes";
	
	Properties installTimes;
	
	private Properties loadInstallTimes()
	{
		Properties props = new Properties();
		try
		{
			props.load(new FileInputStream(new File(getRootDir(), installTimesFileName)));
		} catch (FileNotFoundException e)
		{
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return props;
	}
	
	private void saveInstallTimes()
	{
		try
		{
			installTimes.store(new FileOutputStream(
					new File(getRootDir(), installTimesFileName)),
					"DO NOT EDIT THIS FILE! It keeps track of when you " +
					"installed each of your mods so that MultiMC knows " +
					"which order to add them to minecraft.jar. Editing " +
					"this file could cause problems.");
		} catch (FileNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Long getInstallTime(File file)
	{
		Object value = installTimes.get(file.getAbsolutePath());
		if (value == null)
			return file.lastModified();
		try
		{
			return Long.parseLong(value.toString());
		} catch (NumberFormatException e)
		{
			return file.lastModified();
		}
	}
	
	public void setInstallTime(File file, Long time)
	{
		file.setLastModified(time);
		installTimes.put(file.getAbsolutePath(), time.toString());
		saveInstallTimes();
	}

	public void recursiveSetInstallTime(File file, long time)
	{
		if (file.isDirectory())
		{
			for (File f : file.listFiles())
			{
				recursiveSetInstallTime(f, time);
			}
		}
		else if (file.isFile())
		{
//			file.setLastModified(time);
			setInstallTime(file, time);
		}
	}
	
	// XML Values
	public String getName() { return getXmlNode("name").getTextContent(); }
	public void setName(String v) { getXmlNode("name").setTextContent(v); AutoSave(); }
	
	public String getIconKey() { return getXmlNode("iconKey").getTextContent(); }
	public void setIconKey(String v) { getXmlNode("iconKey").setTextContent(v); AutoSave(); }
	
	public String getNotes() { return getXmlNode("notes").getTextContent(); }
	public void setNotes(String v) { getXmlNode("notes").setTextContent(v); AutoSave(); }
	
	
	// Directories
	/**
	 * @return The instance's root directory
	 */
	public File getRootDir() { return new File(rootDir); }
	
	/**
	 * @return The directory where mods will be stored
	 */
	public File getInstMods() { return new File(rootDir, "instMods"); }
	
	/**
	 * @return The instance's .minecraft folder
	 */
	public File getMCDir() { return new File(rootDir, ".minecraft"); }
	
	/**
	 * @return ModLoader's folder (.minecraft\mods)
	 */
	public File getModLoaderDir() { return new File(getMCDir(), "mods"); }
	
	/**
	 * @return The instance's bin folder (.minecraft\bin)
	 */
	public File getBinDir() { return new File(getMCDir(), "bin"); }
	
	/**
	 * @return The texture packs folder (.minecraft\texturepacks)
	 */
	public File getTexturePackDir() { return new File(getMCDir(), "texturepacks"); }
	
	
	// Files
	/**
	 * @return The instance's data file
	 */
	public File getInstDataFile() { return new File(getRootDir(), InstanceDataFileName); }
}
