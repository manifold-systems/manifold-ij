class MyJsClass_Caret_SetterDeclaration {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get yeah() {
    return this._foo
  }
  set y<caret>eah( value ) {
    this._foo = value
  }
}