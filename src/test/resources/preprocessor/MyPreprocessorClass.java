package preprocessor;

#define BBB

public class MyPreprocessorClass
{
  public void testNested()
  {
    #if BBB
      #error "1"
      #if BBB
        #error "2"
      #else
        boom1
      #endif
      #error "3"
      #if AAA
        boom2
      #elif BBB
        #error "4"
      #endif
     #error "5";
    #elif CCC
      boom3
    #endif
    #error "6"
  }
}