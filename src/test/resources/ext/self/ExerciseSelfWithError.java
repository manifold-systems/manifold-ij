package ext.self;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import manifold.ext.rt.api.Self;

public class ExerciseSelfWithError
{
  public static class Foo<T>
  {
    public @Self Foo<T> getMe() {
      return this;
    }

    public List<@Self Foo<T>> getListFoo() {
      return Collections.singletonList( this );
    }

    public List<? extends @Self Foo<T>> getListFoo2() {
      return Collections.singletonList( this );
    }

    public List<? extends Map<String, @Self Foo<T>>> getListMapFoo() {
      return Collections.singletonList( Collections.singletonMap( "hi", this ) );
    }

    public List<? extends Map<@Self Foo<T>, String>> getListMapFoo2() {
      return Collections.singletonList( Collections.singletonMap( this, "hi" ) );
    }

    public List<? extends Map<String, ? extends @Self Foo<T>>> getListMapFoo3() {
      return Collections.singletonList( Collections.singletonMap( "hi", this ) );
    }

    public List<? extends Map<? extends @Self Foo<T>, String>> getListMapFoo4() {
      return Collections.singletonList( Collections.singletonMap( this, "hi" ) );
    }

    public Map<String, @Self Foo<T>> getMapFoo() {
      return Collections.singletonMap( "hi", this );
    }

    public Map<@Self Foo<T>, String> getMapFoo2() {
      return Collections.singletonMap( this, "hi" );
    }

    public @Self Foo<T>[] getArrayFoo() {
      Object array = Array.newInstance(getClass(), 1);
      Array.set( array, 0, this );
      return (Foo<T>[]) array;
    }

    public Foo<T> @Self[] getArrayFoo2() {
      return getArrayFoo();
    }

    static class Bar<T> extends Foo<T> {
      public void noQualifier() {
        Bar<T> bar = getMe();
        List<Bar<T>> list = getListFoo();
        List<? extends Bar> list2 = getListFoo2();
        List<? extends Map<String, Bar<T>>> list3 = getListMapFoo();
        List<? extends Map<Bar<T>, String>> list4 = getListMapFoo2();
        List<? extends Map<String, ? extends Bar>> list5 = getListMapFoo3();
        List<? extends Map<? extends Bar, String>> list6 = getListMapFoo4();
        Map<String, Bar<T>> map = getMapFoo();
        Map<Bar<T>, String> maps = getMapFoo2();
        Bar[] bars = getArrayFoo();
        Bar[] bars2 = getArrayFoo2();
      }
    }

    public void incompatibleTypeArg() {
      Bar<Date> zeeBar = new Bar<>();
      Bar<String> bar = zeeBar.getMe(); // error: incompatible types
      List<Bar<String>> list = zeeBar.getListFoo(); // error: incompatible types
      List<? extends Bar<String>> list2 =  zeeBar.getListFoo2(); // error: incompatible types
      List<? extends Map<String, Bar<String>>> list3 = zeeBar.getListMapFoo(); // error: incompatible types
      List<? extends Map<Bar<String>, String>> list4 = zeeBar.getListMapFoo2(); // error: incompatible types
      List<? extends Map<String, ? extends Bar<String>>> list5 = zeeBar.getListMapFoo3(); // error: incompatible types
      List<? extends Map<? extends Bar<String>, String>> list6 = zeeBar.getListMapFoo4(); // error: incompatible types
      Map<String, Bar<String>> map = zeeBar.getMapFoo(); // error: incompatible types
      Map<Bar<String>, String> maps = zeeBar.getMapFoo2(); // error: incompatible types
      Bar<String>[] bars = zeeBar.getArrayFoo(); // error: incompatible types
      Bar<String>[] bars2  = zeeBar.getArrayFoo2(); // error: incompatible types
    }

    public void incompatibleVariance() {
      Bar<Date> zeeBar = new Bar<>();
      Foo<Date> bar = zeeBar.getMe();
      List<Foo<Date>> list = zeeBar.getListFoo(); // error: incompatible types
      List<? extends Foo<Date>> list2 =  zeeBar.getListFoo2();
      List<? extends Map<String, Foo<Date>>> list3 = zeeBar.getListMapFoo(); // error: incompatible types
      List<? extends Map<Foo<Date>, String>> list4 = zeeBar.getListMapFoo2(); // error: incompatible types
      List<? extends Map<String, ? extends Foo<Date>>> list5 = zeeBar.getListMapFoo3();
      List<? extends Map<? extends Foo<Date>, String>> list6 = zeeBar.getListMapFoo4();
      Map<String, Foo<Date>> map = zeeBar.getMapFoo(); // error: incompatible types
      Map<Foo<Date>, String> maps = zeeBar.getMapFoo2(); // error: incompatible types
      Foo<Date>[] bars = zeeBar.getArrayFoo();
      Foo<Date>[] bars2  = zeeBar.getArrayFoo2();
    }
  }
}