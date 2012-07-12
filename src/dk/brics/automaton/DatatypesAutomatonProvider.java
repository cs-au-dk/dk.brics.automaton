/*
 * dk.brics.automaton
 * 
 * Copyright (c) 2001-2011 Anders Moeller
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package dk.brics.automaton;

/**
 * Automaton provider based on {@link Datatypes}.
 */
public class DatatypesAutomatonProvider implements AutomatonProvider {
	
	private boolean enable_unicodeblocks, enable_unicodecategories, enable_xml;
	
	/**
	 * Constructs a new automaton provider that recognizes all names
	 * from {@link Datatypes#get(String)}.
	 */
	public DatatypesAutomatonProvider() {
		enable_unicodeblocks = enable_unicodecategories = enable_xml = true;
	}
	
	/**
	 * Constructs a new automaton provider that recognizes some of the names
	 * from {@link Datatypes#get(String)}
	 * @param enable_unicodeblocks if true, enable Unicode block names
	 * @param enable_unicodecategories if true, enable Unicode category names
	 * @param enable_xml if true, enable XML related names
	 */
	public DatatypesAutomatonProvider(boolean enable_unicodeblocks, boolean enable_unicodecategories, boolean enable_xml) {
		this.enable_unicodeblocks = enable_unicodeblocks; 
		this.enable_unicodecategories = enable_unicodecategories;
		this.enable_xml = enable_xml;
	}
	
	public Automaton getAutomaton(String name) {
		if ((enable_unicodeblocks && Datatypes.isUnicodeBlockName(name))
				|| (enable_unicodecategories && Datatypes.isUnicodeCategoryName(name))
				|| (enable_xml && Datatypes.isXMLName(name)))
				return Datatypes.get(name);
		return null;
	}
}
