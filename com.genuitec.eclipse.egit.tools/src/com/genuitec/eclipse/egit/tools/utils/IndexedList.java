/**
 *  Copyright (c) 2015 Genuitec LLC.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Piotr Tomiak <piotr@genuitec.com> - initial API and implementation
 */
package com.genuitec.eclipse.egit.tools.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 */

public final class IndexedList<K,V>
{
    private final List<V> list;
    private final List<V> unmodifiable;
    private final Map<K,V> index;
    
    public IndexedList()
    {
        this.list = new ArrayList<V>();
        this.unmodifiable = Collections.unmodifiableList( this.list );
        this.index = new HashMap<K,V>();
    }
    
    public List<V> getItemList()
    {
        return this.unmodifiable;
    }

    public V getItemByKey( final K key )
    {
        return this.index.get( key );
    }
    
    public boolean containsKey( final K key )
    {
        return this.index.containsKey( key );
    }

    public boolean containsItem( final V item )
    {
        return this.list.contains( item );
    }

    public void addItem( final V item )
    {
        if( item == null )
        {
            throw new IllegalArgumentException();
        }
        
        this.list.add( item );
    }
    
    public void addItemWithKey( final K key,
                                final V item )
    {
        addItem( item );
        addKey( key, item );
    }
    
    public void addKey( final K key,
                        final V item )
    {
        if( key == null || item == null )
        {
            throw new IllegalArgumentException();
        }
        
        if( ! this.list.contains( item ) )
        {
            throw new IllegalArgumentException();
        }
        
        this.index.put( key, item );
    }
    
    public void removeKey( final K key ) {
    	if( key == null ) {
            throw new IllegalArgumentException();
        }
    	
    	this.index.remove(key);
    }
    
    public boolean removeItem( final V item )
    {
        if( this.list.remove( item ) )
        {
            for( Iterator<Map.Entry<K,V>> itr = this.index.entrySet().iterator(); itr.hasNext(); )
            {
                final Map.Entry<K,V> entry = itr.next();
                
                if( entry.getValue() == item )
                {
                    itr.remove();
                }
            }
        
            return true;
        }
        
        return false;
    }

    public boolean removeItemByKey( final K key )
    {
        final V item = this.index.get( key );
        
        if( item != null )
        {
            return removeItem( item );
        }
        
        return false;
    }
    
    public void sort(Comparator<? super V> comparator) {
    	Collections.sort(list, comparator);
    }
    
}