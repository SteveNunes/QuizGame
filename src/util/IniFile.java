package util;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IniFile {
	
	private Path file;
	private String fileName, lastReadVal = null;
	private List<String> fileBuffer;
	private LinkedHashMap<String, LinkedHashMap<String, String>> iniBody;
	private static Map<String, IniFile> openedIniFiles = new HashMap<>();
	
	private IniFile(String fileName, Boolean newFile) {
		this.fileName = fileName;
		refresh();
		if (!newFile)
			loadIniFromDisk(fileName);
		else {
			iniBody = new LinkedHashMap<String, LinkedHashMap<String, String>>();
			fileBuffer = new ArrayList<>();
		}
		openedIniFiles.put(fileName, this);
	}
	
	public static IniFile getNewIniFileInstance(String fileName, Boolean newFile) {
		if (newFile || !openedIniFiles.containsKey(fileName)) 
			return new IniFile(fileName, newFile);
		openedIniFiles.get(fileName).refresh();
		return openedIniFiles.get(fileName);
	}
	
	private void refresh() {
		file = Paths.get(fileName);
		loadIniFromDisk(fileName);
	}

	public static IniFile getNewIniFileInstance(String fileName)
		{ return getNewIniFileInstance(fileName, false); }

	public static List<IniFile> getOpenedIniFilesList()
		{ return (List<IniFile>)openedIniFiles.values(); }

	public void closeFile() {
		openedIniFiles.put(fileName, null);
		openedIniFiles.remove(fileName);
	}
	
	public Path getFilePath()
		{ return file; }

	public static Boolean stringIsSection(String s)
		{ return s.split(" ")[0].matches(("\\Q" + "[*]*" + "\\E").replace("*", "\\E.*\\Q")); }

	public static Boolean stringIsItem(String s) 
		{ return !s.isEmpty() && s.charAt(0) != '=' && s.contains("="); }

	public static String getSectionFromString(String s)
		{ return s.split("]")[0].split(" ")[0].substring(1); }

	public void loadIniFromDisk(String fileName) {
		iniBody = new LinkedHashMap<String, LinkedHashMap<String, String>>();
		if (!fileName.isEmpty()) {
			if (Files.exists(file)) {
				try
					{ fileBuffer = Files.readAllLines(file); }
				catch (Exception e) 
					{ throw new RuntimeException("Unable to open the file " + fileName); }
				String section = "", item, val;
				for (String s : fileBuffer)
					if (stringIsSection(s)) {
						section = getSectionFromString(s);
					  iniBody.put(section, new LinkedHashMap<String, String>());
					}
					else if (!section.isEmpty() && stringIsItem(s)) {
						String[] split = s.split("=");
						item = split[0];
						val = split[1];
						for (int n = 2; n < split.length; n++)
							val += " " + split[n];
						write(section, item, val);
					}
			}
			else
				fileBuffer = new ArrayList<>();
		}
	}

	public void loadIniFromDisk() 
		{ loadIniFromDisk(fileName); }
	
	private void insertMissingItens(String section, List<String> fileBuffer, Map<String, List<String>> items) {
		if (!section.isEmpty()) {
			if (!items.containsKey(section)) {
				if (!fileBuffer.isEmpty())
					fileBuffer.add("");
				fileBuffer.add("[" + section + "]");
				items.put(section, new ArrayList<>());
			}
			for (String i : getItemList(section))
				if (!items.get(section).contains(i)) {
					fileBuffer.add(i + "=" + read(section, i));
					items.get(section).add(i);
				}
		}
	}
	
	private void updateFileBuffer() {
		Map<String, List<String>> addeds = new HashMap<>();
		List<String> newFileBuffer = new ArrayList<>();
		String section = "", item, value, line;
		for (int l = 0, lmax = fileBuffer.size(); l < lmax; l++) {
			line = fileBuffer.get(l);
			if (stringIsSection(line)) {
				insertMissingItens(section, newFileBuffer, addeds);
				section = getSectionFromString(line);
				if (isSection(section)) {
					newFileBuffer.add(line);
					addeds.put(section, new ArrayList<>());
				}
				else {
					section = line = "";
					while (++l < lmax && !stringIsSection(line = fileBuffer.get(l)));
					if (stringIsSection(line))
						l--;
				}
			}
			else if (!section.isEmpty() && stringIsItem(line)) {
				String[] split = line.split("=");
				item = split[0];
				if (isItem(section, item)) {
					value = read(section, item);
					newFileBuffer.add(item + "=" + value);
					addeds.get(section).add(item);
				}
			}
			else
				newFileBuffer.add(line);
		}
		insertMissingItens(section, newFileBuffer, addeds);
		for (String sec : getSectionList())
			insertMissingItens(sec, newFileBuffer, addeds);
		fileBuffer = new ArrayList<>(newFileBuffer);
	}

	public void saveToDisk() {
		updateFileBuffer();
		if (!fileBuffer.isEmpty()) {
			file = Paths.get(fileName);
			try {
				PrintWriter printWriter = new PrintWriter(new FileWriter(fileName));
				for (String s : fileBuffer) 
					printWriter.println(s);
				printWriter.close();
			}
			catch (Exception e)
				{ throw new RuntimeException("Unable to open the file " + fileName); }
		}
	}

	public void write(String iniSection, String iniItem, String value, Boolean saveOnDisk) {
		if (!iniBody.containsKey(iniSection))
		  iniBody.put(iniSection, new LinkedHashMap<String, String>());
		iniBody.get(iniSection).put(iniItem, value);
		if (saveOnDisk)
			saveToDisk();
	}

	public void write(String iniSection, String iniItem, String value)
		{ write(iniSection, iniItem, value, false); }

	public String getLastReadVal()
		{ return lastReadVal; }
	
	public String read(String iniSection, String iniItem) {
		if (isItem(iniSection, iniItem)) 
			return (lastReadVal = iniBody.get(iniSection).get(iniItem));
		return (lastReadVal = null);
	}

	public String remove(String iniSection, String iniItem, Boolean saveOnDisk) {
		if (isItem(iniSection, iniItem)) {
			iniBody.get(iniSection).remove(iniItem);
			if (saveOnDisk)
				saveToDisk();
			return iniItem;
		}
		return null;
	}

	public String remove(String iniSection, String iniItem)
		{ return remove(iniSection, iniItem, false); }

	public String remove(String iniSection, Boolean saveOnDisk) {
		if (isSection(iniSection)) {
			iniBody.remove(iniSection);
			if (saveOnDisk)
				saveToDisk();
			return iniSection;
		}
		return null;
	}

	public String remove(String iniSection)
		{ return remove(iniSection, false); }

	public String fileName()
		{ return fileName; }

	public int getIniSize()
		{ return !iniBody.isEmpty() ? iniBody.size() : 0; }

	public int getSectionPos(String iniSection) {
		if (isSection(iniSection)) {
			Iterator<String> it = iniBody.keySet().iterator();
			for (int n = 0; it.hasNext(); n++)
				if (it.next().equals(iniSection))
					return n + 1;
		}
		return 0;
	}

	public String getSectionAtPos(int pos) {
		if (!iniBody.isEmpty()) {
			List<String> list = getSectionList();
			if (pos - 1 < 0 || pos - 1 >= list.size())
				return null;
			return list.get(pos - 1);
		}
		return null;
	}

	public Boolean isSection(String iniSection)
		{ return !iniBody.isEmpty() ? iniBody.containsKey(iniSection) : false; }

	public int getSectionSize(String iniSection)
		{ return isSection(iniSection) ? iniBody.get(iniSection).size() : -1; }

	public int getItemPos(String iniSection, String iniItem) {
		if (isItem(iniSection, iniItem)) {
			Iterator<String> it = iniBody.get(iniSection).keySet().iterator();
			for (int n = 0; it.hasNext(); n++) 
				if (it.next().equals(iniItem))
					return n + 1;
		}
		return 0;
	}

	public String getItemAtPos(String iniSection, int pos) {
		if (!iniBody.get(iniSection).isEmpty()) {
			List<String> list = getItemList(iniSection);
			if (pos - 1 < 0 || pos - 1 >= list.size())
				return null;
			return list.get(pos - 1);
		}
		return null;
	}

	public Boolean isItem(String iniSection, String iniItem)
		{ return isSection(iniSection) ? iniBody.get(iniSection).containsKey(iniItem) : false; }

	public void clearSection(String iniSection, Boolean saveOnDisk) {
		iniBody.get(iniSection).clear();
		if (saveOnDisk)
			saveToDisk();
	}

	public void clearSection(String iniSection)
		{ clearSection(iniSection, false); }

	public void clearFile(Boolean saveOnDisk) {
		iniBody.clear();
		if (saveOnDisk)
			saveToDisk();
	}

	public void clearFile()
		{ clearFile(false); }

	public List<String> getSectionList() {
		List<String> list = new ArrayList<String>();
		if (!iniBody.isEmpty()) {
			Iterator<String> it = iniBody.keySet().iterator();
			String item;
			while (it.hasNext())
			  if ((item = it.next()) != null && !item.isEmpty())
			  	list.add(item);
		}
		return list;
	}

	public List<String> getItemList(String iniSection) {
		List<String> list = new ArrayList<String>();
		if (isSection(iniSection) && !iniBody.get(iniSection).isEmpty()) {
			Iterator<String> it = iniBody.get(iniSection).keySet().iterator();
			String item;
			while (it.hasNext())
			  if ((item = it.next()) != null && !item.isEmpty())
			  	list.add(item);
		}
		return list;
	}

	/**
	 * Retorna um LinkedHashMap, contendo vários itens=valores provindos de uma
	 * string no formato: {ITEM=VAL}{ITEM2=VAL}{ITEM3=VAL}
	 * {@code enclosers} se refere aos caracteres que isolam o grupo de ITEM=VAL.
	 * 									 Exemplo: Se o formato for {ITEM=VAL} use no {@code enclosers} "{}"
	 * 									 ou simplesmente nem especifique o {@code enclosers}, pois é passado
	 * 									 o valor "{}" por padrão.
	 */
	public static LinkedHashMap<String, String> subItemStringToLinkedHashMap(String val, String enclosers) {
		LinkedHashMap<String, String> subItems = new LinkedHashMap<>();
		Pattern pattern = Pattern.compile("(\\" + enclosers.substring(0, 1) + "[^\\" + enclosers.substring(1, 2) + ".]+\\" + enclosers.substring(1, 2) + ")", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(val);
		StringBuilder result;
		while (matcher.find()) {
			String[] split = matcher.group(0).substring(1, matcher.group(0).length() - 1).split("=");
			val = val.replace(matcher.group(0), "");
			matcher = pattern.matcher(val);
			result = new StringBuilder();
			if (split.length > 1) {
				result.append(split[1]);
				for (int n = 2; n < split.length; n++) {
					result.append("=");
					result.append(split[n]);
				}
			}
			subItems.put(split[0], result.toString());
		}
		return subItems;
	}

	public static LinkedHashMap<String, String> subItemStringToLinkedHashMap(String val)
		{ return subItemStringToLinkedHashMap(val, "{}"); }
	
	public static String linkedHashMapToSubItemString(LinkedHashMap<String, String> map, String enclosers) {
		StringBuilder str = new StringBuilder();
		map.forEach((k, v) -> {
			str.append(enclosers.substring(0, 1));
			str.append(k);
			str.append("=");
			str.append(v);
			str.append(enclosers.substring(1, 2));
		});
		return str.toString();
	}
	
	public static String linkedHashMapToSubItemString(LinkedHashMap<String, String> map)
		{ return linkedHashMapToSubItemString(map, "{}"); }

}