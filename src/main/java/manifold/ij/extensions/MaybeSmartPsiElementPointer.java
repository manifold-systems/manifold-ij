/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;

import java.util.Objects;

public class MaybeSmartPsiElementPointer<E extends PsiElement>
{
  // timeout for an element that remains invalid
  private static final int TIMEOUT = 1000 * 60 * 2; // 2 minutes

  private E _element;
  private SmartPsiElementPointer<E> _smartPointer;
  private final long _timestamp;

  public MaybeSmartPsiElementPointer( E element )
  {
    _element = element;
    _smartPointer = null;
    _timestamp = System.currentTimeMillis();
  }
  public MaybeSmartPsiElementPointer( SmartPsiElementPointer<E> element )
  {
    _smartPointer = element;
    _element = null;
    _timestamp = -1;
  }

  public E getElement()
  {
    if( _smartPointer != null )
    {
      return _smartPointer.getElement();
    }

    if( !_element.isValid() )
    {
      // an element may be temporarily invalid e.g., not fully connected to tree yet.
      // let callers handle this state, see isPermanentlyInvalid() below
      return _element;
    }

    _smartPointer = SmartPointerManagerImpl.createPointer( _element );
    _element = null;
    return getElement();
  }

  public boolean isPermanentlyInvalid()
  {
    return _element != null && !_element.isValid() &&
      System.currentTimeMillis() - _timestamp > TIMEOUT;
  }

  @Override
  public boolean equals( Object o )
  {
    if( this == o ) return true;
    if( o == null || getClass() != o.getClass() ) return false;
    MaybeSmartPsiElementPointer<?> that = (MaybeSmartPsiElementPointer<?>)o;
    return _smartPointer != null && Objects.equals( _smartPointer, that._smartPointer ) ||
      _element != null && Objects.equals( _element, that._element );
  }

  @Override
  public int hashCode()
  {
    return _smartPointer != null ? Objects.hash( _smartPointer ) : Objects.hash( _element );
  }
}
