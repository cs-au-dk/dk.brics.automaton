/*
 * dk.brics.automaton
 * 
 * Copyright (c) 2001-2017 Anders Moeller
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

import java.io.Serializable;
import java.util.Comparator;

class TransitionComparator implements Comparator<Transition>, Serializable {

	static final long serialVersionUID = 10001;

	boolean to_first;
	
	TransitionComparator(boolean to_first) {
		this.to_first = to_first;
	}
	
	/** 
	 * Compares by (min, reverse max, to) or (to, min, reverse max). 
	 */
	public int compare(Transition t1, Transition t2) {
		if (to_first) {
			if (t1.to != t2.to) {
				if (t1.to == null)
					return -1;
				else if (t2.to == null)
					return 1;
				else if (t1.to.number < t2.to.number)
					return -1;
				else if (t1.to.number > t2.to.number)
					return 1;
			}
		}
		if (t1.min < t2.min)
			return -1;
		if (t1.min > t2.min)
			return 1;
		if (t1.max > t2.max)
			return -1;
		if (t1.max < t2.max)
			return 1;
		if (!to_first) {
			if (t1.to != t2.to) {
				if (t1.to == null)
					return -1;
				else if (t2.to == null)
					return 1;
				else if (t1.to.number < t2.to.number)
					return -1;
				else if (t1.to.number > t2.to.number)
					return 1;
			}
		}
		return 0;
	}
}
