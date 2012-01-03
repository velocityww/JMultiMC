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

package forkk.jsettings;


public class IntSetting implements Setting<Integer>
{
	SettingsFile settings;
	Integer defValue;
	String id;
	
	public IntSetting(SettingsFile settings, String id, int defValue)
	{
		settings.addSetting(this);
		this.defValue = defValue;
		this.settings = settings;
		this.id = id;
	}
	
	public IntSetting(SettingsFile settings, String id)
	{
		settings.addSetting(this);
		this.settings = settings;
		this.id = id;
	}
	
	@Override
	public void set(Integer val)
	{
		this.settings.getXmlNode(id, null, defValue.toString()).setTextContent(val.toString());
	}
	
	@Override
	public Integer get()
	{
		return Integer.parseInt(
				this.settings.getXmlNode(id, null, defValue.toString()).getTextContent());
	}
	
	@Override
	public String toString()
	{
		return get().toString();
	}
	
	@Override
	public String getID()
	{
		return id;
	}
}
