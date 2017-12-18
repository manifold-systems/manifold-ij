class MyJsClass_Caret_SetterDeclaration {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get Yeah() {
    return this._foo
  }
  set <caret>Yeah( value ) {
    this._foo = value
  }
}