class MyJsClass_Caret_MethodDeclaration {
  constructor() {
    this._foo = "later"
  }

  h<caret>i() {
  }

  get Yeah() {
    return this._foo
  }
  set Yeah( value ) {
    this._foo = value
  }
}